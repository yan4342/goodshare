"""
Offline evaluation of the hybrid recommender using leave-one-out methodology.
Measures Precision@10 and Recall@10. Results are written to logs/evaluation.log.

If real interaction data is sparse (< 50 qualifying users), faker-generated
synthetic users are added as a fallback.
"""

import os
import logging
import pandas as pd
import numpy as np
from datetime import datetime

# ---------------------------------------------------------------------------
# Logger — writes ONLY to evaluation.log, not recommendation.log
# ---------------------------------------------------------------------------
base_dir = os.path.dirname(os.path.abspath(__file__))
log_dir = os.path.join(base_dir, "logs")
os.makedirs(log_dir, exist_ok=True)

eval_logger = logging.getLogger("Evaluator")
eval_logger.setLevel(logging.DEBUG)
eval_logger.propagate = False  # don't bleed into root / recommendation.log

_fh = logging.FileHandler(os.path.join(log_dir, "evaluation.log"), encoding="utf-8")
_fh.setLevel(logging.DEBUG)
_fh.setFormatter(logging.Formatter("%(asctime)s [%(levelname)s] %(message)s"))
eval_logger.addHandler(_fh)

_sh = logging.StreamHandler()
_sh.setLevel(logging.INFO)
_sh.setFormatter(logging.Formatter("%(asctime)s [%(levelname)s] %(message)s"))
eval_logger.addHandler(_sh)

# ---------------------------------------------------------------------------
# Sparse-data threshold
# ---------------------------------------------------------------------------
SPARSE_THRESHOLD = 50


def _build_fake_interactions(real_df: pd.DataFrame, post_ids: list, n_users: int = 100) -> pd.DataFrame:
    """Generate synthetic interactions using faker when real data is sparse."""
    try:
        from faker import Faker
    except ImportError:
        eval_logger.warning("faker 未安装 — 跳过合成数据生成。请运行: pip install faker")
        return pd.DataFrame(columns=["user_id", "post_id", "score"])

    fake = Faker()
    rng = np.random.default_rng(42)

    # Partition posts into 5 pseudo-categories by post_id modulo
    post_arr = np.array(post_ids)
    n_categories = 5
    records = []

    # Use negative user_ids to avoid collisions with real users
    fake_user_start = -1
    for i in range(n_users):
        uid = fake_user_start - i
        # Each fake user prefers one category
        preferred_cat = i % n_categories
        preferred_posts = post_arr[post_arr % n_categories == preferred_cat]
        other_posts = post_arr[post_arr % n_categories != preferred_cat]

        # 5–15 interactions with preferred posts (high score), 0–3 with others (low score)
        n_pref = min(rng.integers(5, 16), len(preferred_posts))
        n_other = min(rng.integers(0, 4), len(other_posts))

        for pid in rng.choice(preferred_posts, size=n_pref, replace=False):
            records.append({"user_id": uid, "post_id": int(pid), "score": rng.integers(3, 9)})
        for pid in rng.choice(other_posts, size=n_other, replace=False):
            records.append({"user_id": uid, "post_id": int(pid), "score": 1})

    return pd.DataFrame(records)


def evaluate_leave_one_out():
    """
    Leave-one-out evaluation:
    - For each user with ≥2 interactions, hold out the highest-scored item
    - Train on the rest, recommend top-10, check if held-out item is in the list
    - Compute Precision@10 and Recall@10
    """
    eval_logger.info("=" * 60)
    eval_logger.info(f"评估开始于 {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    eval_logger.info("=" * 60)

    # Step 1: Load real interaction data
    from database import fetch_interactions
    real_df = fetch_interactions()
    eval_logger.info(f"从数据库加载了 {len(real_df)} 条真实交互记录")

    if real_df.empty:
        eval_logger.error("未找到交互数据，无法进行评估。")
        return

    # Step 2: Filter users with ≥2 interactions
    user_counts = real_df.groupby("user_id").size()
    qualifying_users = user_counts[user_counts >= 2].index.tolist()
    eval_logger.info(f"拥有 ≥2 条交互的用户数: {len(qualifying_users)}")

    # Step 3: Faker fallback if sparse
    synthetic_df = pd.DataFrame()
    if len(qualifying_users) < SPARSE_THRESHOLD:
        eval_logger.warning(f"真实数据稀疏（< {SPARSE_THRESHOLD} 用户）。正在生成合成数据...")
        post_ids = real_df["post_id"].unique().tolist()
        if len(post_ids) < 10:
            eval_logger.error("数据库中帖子数量太少，无法生成合成数据。中止操作。")
            return
        synthetic_df = _build_fake_interactions(real_df, post_ids, n_users=100)
        eval_logger.info(f"为 {synthetic_df['user_id'].nunique()} 个模拟用户生成了 {len(synthetic_df)} 条合成交互记录")

        # Merge with real data
        combined_df = pd.concat([real_df, synthetic_df], ignore_index=True)
        user_counts = combined_df.groupby("user_id").size()
        qualifying_users = user_counts[user_counts >= 2].index.tolist()
        eval_logger.info(f"添加合成数据后，符合条件的用户数: {len(qualifying_users)}")
    else:
        combined_df = real_df

    # Step 4: Leave-one-out split
    train_records = []
    test_cases = []  # [(user_id, held_out_post_id), ...]

    for uid in qualifying_users:
        user_data = combined_df[combined_df["user_id"] == uid].copy()
        # Hold out the item with highest score (tie-break by largest post_id)
        user_data = user_data.sort_values(by=["score", "post_id"], ascending=[False, False])
        held_out = user_data.iloc[0]
        train_data = user_data.iloc[1:]

        test_cases.append((uid, held_out["post_id"]))
        train_records.append(train_data)

    train_df = pd.concat(train_records, ignore_index=True)
    eval_logger.info(f"训练集: {len(train_df)} 条交互，测试集: {len(test_cases)} 个用户")

    # Step 5: Train a temporary recommender on the train set
    import database as db_module
    from recommender import UserCF

    original_fetch = db_module.fetch_interactions
    db_module.fetch_interactions = lambda: train_df  # monkey-patch

    eval_recommender = UserCF()
    try:
        eval_recommender.train()
    finally:
        db_module.fetch_interactions = original_fetch  # restore

    # Step 6: Evaluate each user
    TOP_K = 10
    hits = 0
    real_hits = 0
    fake_hits = 0
    real_evaluated = 0
    fake_evaluated = 0
    skipped = 0

    for uid, held_out_post in test_cases:
        recs = eval_recommender.recommend(uid, top_k=TOP_K)
        rec_ids = {r["post_id"] for r in recs}
        hit = held_out_post in rec_ids
        if hit:
            hits += 1
            if uid > 0:
                real_hits += 1
            else:
                fake_hits += 1
        if uid > 0:
            real_evaluated += 1
        else:
            fake_evaluated += 1
        eval_logger.debug(f"用户ID={uid} 保留项={held_out_post} 命中={hit} 推荐列表={sorted(rec_ids)}")

    evaluated = len(test_cases) - skipped
    if evaluated == 0:
        eval_logger.error("没有用户可以评估。")
        return

    # For leave-one-out: ground truth size = 1 per user
    precision_at_k = hits / (evaluated * TOP_K)
    recall_at_k = hits / evaluated  # = hits / (evaluated * 1)
    # Calculate metrics for real and fake users separately
    real_precision = real_hits / (real_evaluated * TOP_K) if real_evaluated > 0 else 0.0
    real_recall = real_hits / real_evaluated if real_evaluated > 0 else 0.0
    fake_precision = fake_hits / (fake_evaluated * TOP_K) if fake_evaluated > 0 else 0.0
    fake_recall = fake_hits / fake_evaluated if fake_evaluated > 0 else 0.0

    eval_logger.info("-" * 60)
    eval_logger.info(f"评估结果 (K={TOP_K})")
    eval_logger.info(f"  符合条件的用户总数 : {len(qualifying_users)}")
    eval_logger.info(f"  已评估的用户数      : {evaluated}")
    eval_logger.info(f"  命中次数            : {hits}")
    eval_logger.info(f"  准确率@{TOP_K}           : {precision_at_k:.4f}")
    eval_logger.info(f"  召回率@{TOP_K}             : {recall_at_k:.4f}")
    eval_logger.info(f"  --- 真实用户 ---")
    eval_logger.info(f"    评估用户数        : {real_evaluated}")
    eval_logger.info(f"    命中次数          : {real_hits}")
    eval_logger.info(f"    准确率@{TOP_K}         : {real_precision:.4f}")
    eval_logger.info(f"    召回率@{TOP_K}           : {real_recall:.4f}")
    eval_logger.info(f"  --- 合成用户 ---")
    eval_logger.info(f"    评估用户数        : {fake_evaluated}")
    eval_logger.info(f"    命中次数          : {fake_hits}")
    eval_logger.info(f"    准确率@{TOP_K}         : {fake_precision:.4f}")
    eval_logger.info(f"    召回率@{TOP_K}           : {fake_recall:.4f}")
    if not synthetic_df.empty:
        eval_logger.info(f"  [注意] 使用了合成数据 ({synthetic_df['user_id'].nunique()} 个模拟用户)")
    eval_logger.info("=" * 60)
    eval_logger.info("\n")

    return {"precision_at_k": precision_at_k, "recall_at_k": recall_at_k, "hits": hits, "evaluated": evaluated}


if __name__ == "__main__":
    evaluate_leave_one_out()
