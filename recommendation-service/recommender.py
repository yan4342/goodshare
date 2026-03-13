import pandas as pd
import numpy as np
from sklearn.metrics.pairwise import cosine_similarity
from sklearn.feature_extraction.text import TfidfVectorizer
from database import fetch_interactions, fetch_post_contents

class UserCF:
    def __init__(self):
        self.user_sim_matrix = None
        self.user_item_matrix = None
        self.similarity_df = None
        self.item_similarity_df = None
        self.last_train_time = None
    # 训练用户协同过滤模型
    def train(self):
        print("Training UserCF model...")
        df = fetch_interactions()
        # 检查数据是否为空
        if df.empty:
            print("No interaction data found.")
            return
        # 构建用户-项目交互矩阵
        self.user_item_matrix = df.pivot_table(index='user_id', columns='post_id', values='score').fillna(0)
        # 计算用户相似度矩阵
        sim_matrix = cosine_similarity(self.user_item_matrix)
        # 转换为DataFrame
        self.similarity_df = pd.DataFrame(sim_matrix, index=self.user_item_matrix.index, columns=self.user_item_matrix.index)
        self._train_content_similarity()
        print("Model training completed.")

    # 训练基于内容的推荐模型
    def _train_content_similarity(self):
        content_df = fetch_post_contents()
        if content_df.empty:
            self.item_similarity_df = None
            return
            # 检查内容数据是否为空
        if content_df.empty:
            print("No post content data found.")
            return
        # 构建项目内容矩阵
        vectorizer = TfidfVectorizer(max_features=5000, stop_words=None)
        tfidf_matrix = vectorizer.fit_transform(content_df['content_text'])
        sim_matrix = cosine_similarity(tfidf_matrix)
            # 转换为DataFrame
        self.item_similarity_df = pd.DataFrame(sim_matrix, index=content_df['post_id'], columns=content_df['post_id'])

    # 基于用户协同过滤推荐
    def _recommend_user_cf(self, user_id, top_k=20, n_similar_users=10):
        if self.user_item_matrix is None or user_id not in self.user_item_matrix.index:
            return []

        if user_id not in self.similarity_df.index:
             return []
        # 找到与目标用户最相似的n个用户
        similar_users = self.similarity_df[user_id].sort_values(ascending=False).iloc[1:n_similar_users+1]
        # 找到目标用户已交互的项目，排除已交互项目
        target_user_items = set(self.user_item_matrix.loc[user_id][self.user_item_matrix.loc[user_id] > 0].index)
        
        recommendations = {}
        # 遍历相似用户的交互记录
        for sim_user, similarity in similar_users.items():
            user_interactions = self.user_item_matrix.loc[sim_user]
            interacted_items = user_interactions[user_interactions > 0].index
            
            for item in interacted_items:
                if item not in target_user_items:
                    if item not in recommendations:
                        recommendations[item] = 0
                    recommendations[item] += similarity * user_interactions[item]
        
        sorted_recs = sorted(recommendations.items(), key=lambda x: x[1], reverse=True)
        
        return [{"post_id": item, "score": score} for item, score in sorted_recs[:top_k]]
    # 基于内容协同过滤推荐
    def _recommend_content_cf(self, user_id, top_k=20, max_seed_items=20, max_sim_items=50):
        if self.user_item_matrix is None or self.item_similarity_df is None:
            return []
        if user_id not in self.user_item_matrix.index:
            return []
        target_vector = self.user_item_matrix.loc[user_id]
        interacted = target_vector[target_vector > 0].sort_values(ascending=False)
        if interacted.empty:
            return []
        seed_items = interacted.index[:max_seed_items]
        recommendations = {}
        target_user_items = set(interacted.index)
        for seed_id in seed_items:
            if seed_id not in self.item_similarity_df.index:
                continue
            sim_series = self.item_similarity_df.loc[seed_id].sort_values(ascending=False).iloc[1:max_sim_items+1]
            seed_weight = interacted.loc[seed_id]
            for item_id, sim_score in sim_series.items():
                if item_id in target_user_items:
                    continue
                if item_id not in recommendations:
                    recommendations[item_id] = 0
                recommendations[item_id] += sim_score * seed_weight
        sorted_recs = sorted(recommendations.items(), key=lambda x: x[1], reverse=True)
        return [{"post_id": item, "score": score} for item, score in sorted_recs[:top_k]]
    # 推荐帖子，结合用户协同过滤和内容协同过滤
    def recommend(self, user_id, top_k=20, n_similar_users=10, user_cf_weight=0.7, content_cf_weight=0.3):
        user_cf_recs = self._recommend_user_cf(user_id, top_k=top_k, n_similar_users=n_similar_users)
        content_cf_recs = self._recommend_content_cf(user_id, top_k=top_k)
        combined = {}
        for rec in user_cf_recs:
            combined[rec["post_id"]] = combined.get(rec["post_id"], 0) + rec["score"] * user_cf_weight
        for rec in content_cf_recs:
            combined[rec["post_id"]] = combined.get(rec["post_id"], 0) + rec["score"] * content_cf_weight
        sorted_recs = sorted(combined.items(), key=lambda x: x[1], reverse=True)
        return [{"post_id": item, "score": score} for item, score in sorted_recs[:top_k]]

# Global instance
recommender = UserCF()
