import pandas as pd
import numpy as np
import logging
import os
from sklearn.metrics.pairwise import cosine_similarity
from sklearn.feature_extraction.text import TfidfVectorizer
from database import fetch_interactions, fetch_post_contents

# 配置日志
# 获取当前脚本所在的绝对路径，确保 logs 文件夹生成在 recommendation-service 目录下
base_dir = os.path.dirname(os.path.abspath(__file__))
log_dir = os.path.join(base_dir, "logs")

if not os.path.exists(log_dir):
    os.makedirs(log_dir)

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - [%(levelname)s] - %(message)s',
    handlers=[
        logging.FileHandler(os.path.join(log_dir, "recommendation.log"), encoding='utf-8'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger("Recommender")

class UserCF:
    def __init__(self):
        self.user_sim_matrix = None
        self.user_item_matrix = None
        self.similarity_df = None
        self.item_similarity_df = None
        self.last_train_time = None
    # 训练用户协同过滤模型
    def train(self):
        logger.info("\n开始训练用户协同过滤模型...")
        df = fetch_interactions()
        # 检查数据是否为空
        if df.empty:
            logger.warning("未找到用户交互数据，跳过训练。")
            return
        # 构建用户-项目交互矩阵
        self.user_item_matrix = df.pivot_table(index='user_id', columns='post_id', values='score').fillna(0)
        # 计算用户相似度矩阵
        sim_matrix = cosine_similarity(self.user_item_matrix)
        # 转换为DataFrame
        self.similarity_df = pd.DataFrame(sim_matrix, index=self.user_item_matrix.index, columns=self.user_item_matrix.index)
        self._train_content_similarity()
        logger.info("模型训练完成。")

    # 训练基于内容的推荐模型
    def _train_content_similarity(self):
        content_df = fetch_post_contents()
        if content_df.empty:
            self.item_similarity_df = None
            logger.warning("未找到帖子内容数据。内容协同过滤将失效。")
            return
        
        # 构建项目内容矩阵
        vectorizer = TfidfVectorizer(max_features=5000, stop_words=None)
        tfidf_matrix = vectorizer.fit_transform(content_df['content_text'])
        sim_matrix = cosine_similarity(tfidf_matrix)
        # 转换为DataFrame
        self.item_similarity_df = pd.DataFrame(sim_matrix, index=content_df['post_id'], columns=content_df['post_id'])
        
        # 额外保存一份 post_id 到 tags 的映射，方便打印日志
        self.post_tags_map = content_df.set_index('post_id')['tags'].to_dict() if 'tags' in content_df.columns else {}

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
            logger.debug(f"内容协同过滤被跳过：矩阵未初始化 (user_item_matrix is None: {self.user_item_matrix is None}, item_similarity_df is None: {self.item_similarity_df is None})")
            return []
        if user_id not in self.user_item_matrix.index:
            logger.debug(f"内容协同过滤被跳过：用户 {user_id} 没有交互记录")
            return []
        
        target_vector = self.user_item_matrix.loc[user_id]
        interacted = target_vector[target_vector > 0].sort_values(ascending=False)
        if interacted.empty:
            logger.debug(f"内容协同过滤被跳过：用户 {user_id} 交互分数均小于等于 0")
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
                # 只保留相似度大于0的项目
                if sim_score > 0:
                    if item_id not in recommendations:
                        recommendations[item_id] = 0
                    recommendations[item_id] += sim_score * seed_weight
                    
        sorted_recs = sorted(recommendations.items(), key=lambda x: x[1], reverse=True)
        return [{"post_id": item, "score": score} for item, score in sorted_recs[:top_k]]
    # 推荐帖子，结合用户协同过滤和内容协同过滤
    def recommend(self, user_id, top_k=20, n_similar_users=10, user_cf_weight=0.7, content_cf_weight=0.3):
        logger.info(f"开始为用户 {user_id} 生成推荐...")
        user_cf_recs = self._recommend_user_cf(user_id, top_k=top_k, n_similar_users=n_similar_users)
        content_cf_recs = self._recommend_content_cf(user_id, top_k=top_k)
        
        logger.info(f"召回结果 - 用户协同过滤候选集: {len(user_cf_recs)} 个, 内容协同过滤候选集: {len(content_cf_recs)} 个")
        
        combined = {}
        # 记录来源明细，方便验证
        details = {}
        
        for rec in user_cf_recs:
            post_id = rec["post_id"]
            score = rec["score"] * user_cf_weight
            combined[post_id] = combined.get(post_id, 0) + score
            details.setdefault(post_id, {})["user_cf"] = score
            
        for rec in content_cf_recs:
            post_id = rec["post_id"]
            score = rec["score"] * content_cf_weight
            combined[post_id] = combined.get(post_id, 0) + score
            details.setdefault(post_id, {})["content_cf"] = score
            
        sorted_recs = sorted(combined.items(), key=lambda x: x[1], reverse=True)
        top_results = [{"post_id": item, "score": score} for item, score in sorted_recs[:top_k]]
        
        # 打印 Top K 推荐结果和来源细节
        logger.info(f"用户 {user_id} 的 Top {top_k} 推荐结果:")
        for i, res in enumerate(top_results):
            post_id = res['post_id']
            score = res['score']
            detail = details.get(post_id, {})
            u_score = detail.get('user_cf', 0.0)
            c_score = detail.get('content_cf', 0.0)
            
            # 判断主要来源
            if u_score > 0 and c_score > 0:
                source = "双端召回"
            elif u_score > 0:
                source = "仅用户协同(UserCF)"
            elif c_score > 0:
                source = "仅内容协同(ContentCF)"
            else:
                source = "未知"
                
            # 获取帖子标签
            tags = self.post_tags_map.get(post_id, "") if hasattr(self, 'post_tags_map') else ""
            tags_display = f" | 标签: [{tags}]" if tags else ""
            
            logger.info(f"  {i+1}. 帖子ID: {post_id}{tags_display} | 总分: {score:.4f} (UserCF: {u_score:.4f}, ContentCF: {c_score:.4f}) | 来源: {source}")
            
        return top_results

# Global instance
recommender = UserCF()
