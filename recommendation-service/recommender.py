import pandas as pd
import numpy as np
from sklearn.metrics.pairwise import cosine_similarity
from database import fetch_interactions

class UserCF:
    def __init__(self):
        self.user_sim_matrix = None
        self.user_item_matrix = None
        self.similarity_df = None
        self.last_train_time = None

    def train(self):
        """
        Build User-Item Matrix and compute User Similarity Matrix.
        """
        print("Training UserCF model...")
        df = fetch_interactions()
        
        if df.empty:
            print("No interaction data found.")
            return

        # Create User-Item Matrix (rows: users, cols: items, values: score)
        self.user_item_matrix = df.pivot_table(index='user_id', columns='post_id', values='score').fillna(0)
        
        # Compute Cosine Similarity between users
        # sklearn's cosine_similarity returns a matrix
        sim_matrix = cosine_similarity(self.user_item_matrix)
        
        # Convert to DataFrame for easier lookup
        self.similarity_df = pd.DataFrame(sim_matrix, index=self.user_item_matrix.index, columns=self.user_item_matrix.index)
        print("Model training completed.")

    def recommend(self, user_id, top_k=20, n_similar_users=10):
        """
        Recommend items for a user based on similar users.
        """
        if self.user_item_matrix is None or user_id not in self.user_item_matrix.index:
            return []

        # Get top N similar users (excluding self)
        if user_id not in self.similarity_df.index:
             return []
             
        similar_users = self.similarity_df[user_id].sort_values(ascending=False).iloc[1:n_similar_users+1]
        
        # Get items interacted by similar users but not by target user
        target_user_items = set(self.user_item_matrix.loc[user_id][self.user_item_matrix.loc[user_id] > 0].index)
        
        recommendations = {}
        
        for sim_user, similarity in similar_users.items():
            # Get items from this similar user
            user_interactions = self.user_item_matrix.loc[sim_user]
            interacted_items = user_interactions[user_interactions > 0].index
            
            for item in interacted_items:
                if item not in target_user_items:
                    # Score = Sum(Similarity * Rating)
                    if item not in recommendations:
                        recommendations[item] = 0
                    recommendations[item] += similarity * user_interactions[item]
        
        # Sort by score
        sorted_recs = sorted(recommendations.items(), key=lambda x: x[1], reverse=True)
        
        # Return top K post_ids
        return [{"post_id": item, "score": score} for item, score in sorted_recs[:top_k]]

# Global instance
recommender = UserCF()
