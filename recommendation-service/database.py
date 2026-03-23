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

def fetch_post_contents():
    try:
        # 修改查询：将 GROUP_CONCAT 的大小限制调大，或者不在 SQL 层做 GROUP_CONCAT，而是在 Pandas 层做聚合，以避免 Out of sort memory 错误
        # 方法2：在 Pandas 中进行 GroupBy 聚合更安全，且不依赖 MySQL 的 sort buffer
        query = """
            SELECT 
                p.id AS post_id,
                p.title,
                p.content,
                t.name AS tag_name
            FROM posts p
            LEFT JOIN post_tags pt ON p.id = pt.post_id
            LEFT JOIN tags t ON t.id = pt.tag_id
            WHERE p.status IS NULL OR p.status != 2
        """
        df_raw = pd.read_sql(query, engine)
        
        # 调试：打印查询出的原始数据条数
        print(f"DEBUG: Fetched {len(df_raw)} rows from database for post contents.")
        
        if df_raw.empty:
            return pd.DataFrame(columns=['post_id', 'content_text', 'tags'])
            
        # 在 Pandas 层聚合 tags
        df_raw['tag_name'] = df_raw['tag_name'].astype(str).replace(['nan', 'None', 'NULL'], '')
        
        # 将相同 post_id 的 tag_name 拼接成一个用空格分隔的字符串
        tags_agg = df_raw.groupby('post_id')['tag_name'].apply(lambda x: ' '.join(x[x != ''])).reset_index()
        tags_agg.rename(columns={'tag_name': 'tags'}, inplace=True)
        
        # 对 posts 去重 (因为 left join 导致会有重复的帖子记录)
        posts_df = df_raw[['post_id', 'title', 'content']].drop_duplicates(subset=['post_id'])
        
        # 合并 tags
        df = pd.merge(posts_df, tags_agg, on='post_id', how='left')
        
        # 处理空值并拼接 content_text
        df['title'] = df['title'].astype(str).replace(['nan', 'None', 'NULL'], '')
        df['content'] = df['content'].astype(str).replace(['nan', 'None', 'NULL'], '')
        df['tags'] = df['tags'].astype(str).replace(['nan', 'None', 'NULL'], '')
        
        df['content_text'] = (df['title'] + ' ' + df['content'] + ' ' + df['tags']).str.strip()
        
        # 过滤掉内容全为空的行
        df = df[df['content_text'] != '']
        
        print(f"DEBUG: After cleaning, {len(df)} unique posts remain for content CF.")
        
        return df[['post_id', 'content_text', 'tags']]
    except Exception as e:
        import traceback
        print(f"Error fetching post contents: {e}")
        traceback.print_exc()
        return pd.DataFrame(columns=['post_id', 'content_text', 'tags'])
