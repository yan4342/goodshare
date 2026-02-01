from sqlalchemy import create_engine
import pandas as pd
import os

# Database Configuration
# Using localhost because this service runs on the host or network_mode: host
DB_URL = os.getenv("DB_URL", "mysql+pymysql://root:123456@localhost:3306/goodshare")

engine = create_engine(DB_URL)

def fetch_interactions():
    """
    Fetch interactions from multiple tables and aggregate them into a single DataFrame.
    Weights: View=1, Like=3, Comment=5, Favorite=5
    """
    try:
        # Fetch data
        views = pd.read_sql("SELECT user_id, post_id FROM post_views", engine)
        likes = pd.read_sql("SELECT user_id, post_id FROM post_likes", engine)
        comments = pd.read_sql("SELECT user_id, post_id FROM comments", engine)
        favorites = pd.read_sql("SELECT user_id, post_id FROM favorites", engine)

        # Assign weights
        views['score'] = 1
        likes['score'] = 3
        comments['score'] = 5
        favorites['score'] = 5

        # Concatenate
        df = pd.concat([views, likes, comments, favorites], ignore_index=True)

        # Aggregate scores (sum scores if multiple interactions exist for same user-post)
        df_agg = df.groupby(['user_id', 'post_id'])['score'].sum().reset_index()
        
        return df_agg
    except Exception as e:
        print(f"Error fetching data: {e}")
        return pd.DataFrame(columns=['user_id', 'post_id', 'score'])
