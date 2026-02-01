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
        recs = recommender.recommend(user_id, top_k=limit)
        return recs
    except Exception as e:
        print(f"Error generating recommendations: {e}")
        return []

@app.get("/health")
def health():
    return {"status": "ok"}

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=5000, reload=True)
