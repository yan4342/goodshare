import os
from fastapi import FastAPI, Body
from recommender import recommender, logger
import uvicorn
import asyncio
from contextlib import asynccontextmanager


async def run_startup_evaluation():
    """Run offline evaluation in a worker thread so startup stays responsive."""
    try:
        from evaluate import evaluate_leave_one_out

        logger.info("服务启动完成，开始在后台执行离线评估...")
        await asyncio.to_thread(evaluate_leave_one_out)
        logger.info("后台离线评估执行完成。")
    except Exception as exc:
        logger.exception(f"后台离线评估执行失败: {exc}")

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: Train model
    recommender.train()
    # Periodically retrain (simplified for this example, usually separate worker)
    periodic_task = asyncio.create_task(periodic_train())

    tasks_to_cancel = [periodic_task]

    yield
    # Shutdown
    for task in tasks_to_cancel:
        task.cancel()

    await asyncio.gather(*tasks_to_cancel, return_exceptions=True)

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

@app.get("/retrain")
def retrain_model():
    """手动触发模型重训（评估脚本调用）"""
    try:
        logger.info("手动触发模型重训...")
        recommender.train()
        logger.info("手动重训完成。")
        return {"status": "ok", "message": "Model retrained successfully"}
    except Exception as e:
        logger.error(f"手动重训失败: {e}")
        return {"status": "error", "message": str(e)}

@app.post("/update-weights")
def update_weights(weights: dict = Body(...)):
    """
    接收 Java 端推送的交互权重并触发模型重训。
    请求体示例: {"view": 1, "like": 3, "comment": 2, "favorite": 5}
    """
    try:
        logger.info(f"接收到权重更新请求: {weights}")
        recommender.update_interaction_weights(weights)
        # 权重变更后触发重训
        logger.info("权重已更新，开始重训模型...")
        recommender.train()
        logger.info("权重更新并重训完成。")
        return {
            "status": "ok",
            "message": "Weights updated and model retrained",
            "current_weights": recommender.get_interaction_weights()
        }
    except Exception as e:
        logger.error(f"更新权重失败: {e}")
        return {"status": "error", "message": str(e)}

@app.get("/weights")
def get_weights():
    """获取当前交互权重"""
    return recommender.get_interaction_weights()

@app.get("/health")
def health():
    return {"status": "ok"}

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=5000, reload=True)
