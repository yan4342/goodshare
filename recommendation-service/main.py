from fastapi import FastAPI, BackgroundTasks
from recommender import recommender
import uvicorn
import asyncio
from contextlib import asynccontextmanager

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: Train model
    recommender.train()
    # Periodically retrain (simplified for this example, usually separate worker)
    asyncio.create_task(periodic_train())
    yield
    # Shutdown

app = FastAPI(lifespan=lifespan)

async def periodic_train():
    while True:
        await asyncio.sleep(300) # Retrain every 5 minutes
        recommender.train()

@app.get("/recommend")
def get_recommendations(user_id: int, limit: int = 20):
    try:
        from recommender import logger # 引入日志
        logger.info(f"接收到推荐请求: user_id={user_id}, limit={limit}")
        recs = recommender.recommend(user_id, top_k=limit)
        return recs
    except Exception as e:
        from recommender import logger
        logger.error(f"生成推荐时发生错误: {e}")
        return []

@app.get("/health")
def health():
    return {"status": "ok"}

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=5000, reload=True)
