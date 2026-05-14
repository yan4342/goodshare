"""
离线评估系统 — 混合推荐算法
=================================
评估方法: 隐式反馈 Train-Test Split + 多指标评估
  - 将用户交互数据按 80/20 比例拆分为训练集和测试集
  - 训练推荐模型后，对每个用户生成 Top-K 推荐
  - 测量推荐结果与测试集中的高分交互的重叠度

评估指标:
  - Precision@K, Recall@K, NDCG@K (准确性)
  - Hit Rate@K (命中率)
  - Catalog Coverage@K (覆盖率)
  - Intra-List Diversity@K (多样性)

对比策略: Hybrid, UserCF, ContentBase, ItemCF
对比 K 值: 5, 10, 20

当真实交互数据不足时，自动生成高仿真合成数据。
图表输出到 logs/evaluation_charts/ 目录。
"""

import os
import sys
import logging
import warnings
import numpy as np
import pandas as pd
from datetime import datetime
from collections import defaultdict

# ---------------------------------------------------------------------------
# 日志配置
# ---------------------------------------------------------------------------
base_dir = os.path.dirname(os.path.abspath(__file__))
log_dir = os.path.join(base_dir, "logs")
os.makedirs(log_dir, exist_ok=True)

eval_logger = logging.getLogger("Evaluator")
eval_logger.setLevel(logging.DEBUG)
eval_logger.propagate = False

_fh = logging.FileHandler(os.path.join(log_dir, "evaluation.log"), encoding="utf-8")
_fh.setLevel(logging.DEBUG)
_fh.setFormatter(logging.Formatter("%(asctime)s [%(levelname)s] %(message)s"))
eval_logger.addHandler(_fh)

_sh = logging.StreamHandler()
_sh.setLevel(logging.INFO)
_sh.setFormatter(logging.Formatter("%(asctime)s [%(levelname)s] %(message)s"))
eval_logger.addHandler(_sh)

warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", category=UserWarning)

# ---------------------------------------------------------------------------
# 全局常量
# ---------------------------------------------------------------------------
K_VALUES = [5, 10, 20]
SPARSE_THRESHOLD = 200
NUM_SYNTHETIC_USERS = 300
N_CATEGORIES = 5
TEST_RATIO = 0.2     # 20% 的高分交互作为测试集

CHART_DIR = os.path.join(log_dir, "evaluation_charts")
os.makedirs(CHART_DIR, exist_ok=True)


# ═══════════════════════════════════════════════════════════════════════════
# 1. 合成数据生成器
# ═══════════════════════════════════════════════════════════════════════════

def _build_synthetic_interactions(real_df: pd.DataFrame,
                                  n_users: int = NUM_SYNTHETIC_USERS,
                                  seed: int = 42) -> pd.DataFrame:
    """
    生成高仿真合成交互数据，模拟真实用户行为的多样性。

    设计要点（避免合成数据过于"干净"导致策略趋同）：
    - 每个用户有独立的"品味种子"，即使同偏好类别也只交互帖子的子集
    - 增加"噪声交互"：部分交互来自非偏好类别（模拟偶然发现）
    - 模拟不同活跃度：低/中/高活跃用户交互数量差异大
    - 偏好重叠而非完全一致：相似用户共享部分而非全部交互
    """
    rng = np.random.default_rng(seed)

    post_ids = real_df["post_id"].unique()
    post_arr = np.array(sorted(post_ids))

    post_category = {int(pid): int(pid) % N_CATEGORIES for pid in post_arr}
    category_posts = defaultdict(list)
    for pid, cat in post_category.items():
        category_posts[cat].append(pid)

    max_real_uid = int(real_df["user_id"].max()) + 1
    records = []

    for i in range(n_users):
        uid = max_real_uid + i

        # 每个用户有自己的"品味种子"（决定具体交互哪些帖子）
        user_rng = np.random.default_rng(rng.integers(0, 10000))

        # 活跃度分层：低(30%)、中(50%)、高(20%)
        activity_roll = rng.random()
        if activity_roll < 0.3:
            total_target = user_rng.integers(3, 9)
        elif activity_roll < 0.8:
            total_target = user_rng.integers(8, 21)
        else:
            total_target = user_rng.integers(15, 36)

        # 偏好类别
        if rng.random() < 0.5:
            preferred_cats = [int(rng.integers(0, N_CATEGORIES))]
        else:
            preferred_cats = [int(c) for c in rng.choice(N_CATEGORIES, size=2, replace=False)]

        # 偏好类别交互（占 85%）
        n_pref = max(1, int(total_target * 0.85))
        for cat in preferred_cats:
            available = category_posts.get(cat, [])
            if not available:
                continue
            # 每个用户只交互偏好类别中的一部分帖子（模拟个人品味差异）
            subset_size = max(1, int(len(available) * user_rng.uniform(0.3, 0.8)))
            subset = user_rng.choice(available, size=min(subset_size, len(available)), replace=False)
            n_cat = max(1, min(n_pref // max(len(preferred_cats), 1), len(subset)))
            chosen = user_rng.choice(subset, size=n_cat, replace=False)
            for pid in chosen:
                score = int(user_rng.integers(3, 9))
                records.append({"user_id": uid, "post_id": int(pid), "score": score})

        # 噪声交互（15%，模拟"偶然发现"）
        n_noise = max(1, total_target - n_pref)
        non_preferred = [c for c in range(N_CATEGORIES) if c not in preferred_cats]
        for _ in range(n_noise):
            noise_cat = user_rng.choice(non_preferred)
            available = category_posts.get(noise_cat, [])
            if not available:
                continue
            pid = user_rng.choice(available)
            score = int(user_rng.integers(1, 4))
            records.append({"user_id": uid, "post_id": int(pid), "score": score})

    df = pd.DataFrame(records)
    df = df.groupby(["user_id", "post_id"])["score"].sum().reset_index()
    eval_logger.info(f"合成交互: {df['user_id'].nunique()} 用户, "
                     f"{df['post_id'].nunique()} 帖子, "
                     f"{len(df)} 条记录")
    return df


# ═══════════════════════════════════════════════════════════════════════════
# 2. 评估指标函数
# ═══════════════════════════════════════════════════════════════════════════

def precision_at_k(recommended: set, relevant: set, k: int) -> float:
    if k == 0:
        return 0.0
    return len(recommended & relevant) / k


def recall_at_k(recommended: set, relevant: set, k: int) -> float:
    if len(relevant) == 0:
        return 0.0
    return len(recommended & relevant) / len(relevant)


def ndcg_at_k(recommended_list: list, relevant: set, k: int) -> float:
    dcg = 0.0
    for i, item in enumerate(recommended_list[:k]):
        if item in relevant:
            dcg += 1.0 / np.log2(i + 2)
    ideal_len = min(len(relevant), k)
    idcg = sum(1.0 / np.log2(i + 2) for i in range(ideal_len))
    return dcg / idcg if idcg > 0 else 0.0


def hit_rate_at_k(all_recommended: dict, all_relevant: dict, k: int) -> float:
    hits = 0
    total = 0
    for uid in all_relevant:
        if uid in all_recommended:
            rec_set = set(all_recommended[uid][:k])
            if rec_set & all_relevant[uid]:
                hits += 1
            total += 1
    return hits / total if total > 0 else 0.0


def catalog_coverage(all_recommended: dict, all_item_ids: set, k: int) -> float:
    rec_items = set()
    for uid, rec_list in all_recommended.items():
        rec_items.update(rec_list[:k])
    if len(all_item_ids) == 0:
        return 0.0
    return min(len(rec_items) / len(all_item_ids), 1.0)


def intra_list_diversity(recommended_list: list, item_similarity_df, k: int) -> float:
    if item_similarity_df is None:
        return 0.0
    items = [i for i in recommended_list[:k] if i in item_similarity_df.index]
    if len(items) < 2:
        return 1.0
    total_sim = 0.0
    count = 0
    for i in range(len(items)):
        for j in range(i + 1, len(items)):
            if items[j] in item_similarity_df.columns:
                total_sim += item_similarity_df.loc[items[i], items[j]]
                count += 1
    return 1.0 - (total_sim / count) if count > 0 else 1.0


# ═══════════════════════════════════════════════════════════════════════════
# 3. 数据加载与拆分
# ═══════════════════════════════════════════════════════════════════════════

def _load_data():
    from database import fetch_interactions

    real_df = fetch_interactions()
    eval_logger.info(f"从数据库加载了 {len(real_df)} 条真实交互记录")

    if real_df.empty:
        eval_logger.error("未找到交互数据，无法进行评估。")
        return None, None, True

    user_counts = real_df.groupby("user_id").size()
    qualifying_real = user_counts[user_counts >= 3].index.tolist()
    eval_logger.info(f"拥有 ≥3 条交互的真实用户数: {len(qualifying_real)}")

    has_synthetic = False
    if len(qualifying_real) < SPARSE_THRESHOLD:
        eval_logger.warning(f"真实数据稀疏（< {SPARSE_THRESHOLD} 用户），正在生成合成数据...")
        synthetic_df = _build_synthetic_interactions(real_df)
        combined_df = pd.concat([real_df, synthetic_df], ignore_index=True)
        has_synthetic = True
    else:
        combined_df = real_df

    user_counts = combined_df.groupby("user_id").size()
    qualifying = user_counts[user_counts >= 3].index.tolist()
    eval_logger.info(f"最终符合条件的用户数: {len(qualifying)}")

    return combined_df, qualifying, has_synthetic


def _train_test_split(combined_df: pd.DataFrame, qualifying_users: list):
    """
    Train-Test Split for implicit feedback evaluation.
    For each qualifying user, sort interactions by score descending,
    put the top TEST_RATIO fraction into the test set (as 'relevant' items),
    and the rest into the training set.
    Only items with score >= 3 are eligible for the test set.
    """
    rng = np.random.default_rng(42)
    train_records = []
    test_cases = []  # [(user_id, {relevant_post_ids}), ...]

    for uid in qualifying_users:
        user_data = combined_df[combined_df["user_id"] == uid].copy()

        # Only consider high-score items (>= 3) as potential test items
        high_score = user_data[user_data["score"] >= 3].sort_values(
            by=["score", "post_id"], ascending=[False, False])
        low_score = user_data[user_data["score"] < 3]

        if len(high_score) < 2:
            # Not enough high-score items; use all as training
            train_records.append(user_data)
            continue

        # Take ~20% of high-score items (at least 1, at most 5) as test
        n_test = max(1, min(5, max(1, int(len(high_score) * TEST_RATIO))))
        # Random selection from high-score items
        holdout_idx = rng.choice(len(high_score), size=n_test, replace=False)
        test_items = high_score.iloc[holdout_idx]
        train_from_high = high_score.drop(high_score.index[holdout_idx])

        train_data = pd.concat([train_from_high, low_score], ignore_index=True)
        relevant_posts = set(int(p) for p in test_items["post_id"].tolist())

        if len(train_data) >= 1:
            train_records.append(train_data)
            test_cases.append((int(uid), relevant_posts))
        else:
            train_records.append(user_data)

    train_df = pd.concat(train_records, ignore_index=True)
    avg_rel = np.mean([len(r) for _, r in test_cases]) if test_cases else 0
    eval_logger.info(f"训练集: {len(train_df)} 条交互，测试用户: {len(test_cases)} 个（平均相关项: {avg_rel:.1f}）")
    return train_df, test_cases


# ═══════════════════════════════════════════════════════════════════════════
# 4. 模型训练与评估
# ═══════════════════════════════════════════════════════════════════════════

def _train_eval_recommender(train_df: pd.DataFrame):
    """
    在训练集上训练推荐器。
    必须同时 patch database 模块和 recommender 模块中的引用。
    """
    import database as db_module
    import recommender as rec_module
    from recommender import HybridRecommender

    original_db_fetch = db_module.fetch_interactions
    original_rec_fetch = rec_module.fetch_interactions
    db_module.fetch_interactions = lambda: train_df
    rec_module.fetch_interactions = lambda: train_df

    eval_rec = HybridRecommender()
    try:
        eval_rec.train()
    finally:
        db_module.fetch_interactions = original_db_fetch
        rec_module.fetch_interactions = original_rec_fetch

    return eval_rec


def _evaluate_strategy(recommender, strategy_name: str,
                       test_cases: list, all_item_ids: set, top_k: int):
    strategy_map = {
        "hybrid": lambda uid, k: recommender.recommend(uid, top_k=k),
        "user_cf": lambda uid, k: recommender._recommend_user_cf(uid, top_k=k),
        "content_cf": lambda uid, k: recommender._recommend_content_based(uid, top_k=k),
        "item_cf": lambda uid, k: recommender._recommend_item_cf(uid, top_k=k),
    }
    recommend_fn = strategy_map.get(strategy_name)
    if recommend_fn is None:
        return {}

    precisions, recalls, ndcgs = [], [], []
    all_rec_lists, all_relevant = {}, {}
    empty_count = 0
    diag_logged = False

    for uid, relevant in test_cases:
        try:
            recs = recommend_fn(uid, top_k)
        except Exception:
            recs = []

        rec_ids_list = [r["post_id"] for r in recs]
        rec_ids_set = set(rec_ids_list)

        if not rec_ids_list:
            empty_count += 1

        p = precision_at_k(rec_ids_set, relevant, top_k)
        r = recall_at_k(rec_ids_set, relevant, top_k)
        n = ndcg_at_k(rec_ids_list, relevant, top_k)
        precisions.append(p)
        recalls.append(r)
        ndcgs.append(n)

        all_rec_lists[uid] = rec_ids_list
        all_relevant[uid] = relevant

        # Diagnostic for first non-empty recommendation
        if not diag_logged and strategy_name == "hybrid" and rec_ids_list:
            diag_logged = True
            overlap = rec_ids_set & relevant
            eval_logger.debug(f"  [DIAG] uid={uid} relevant={relevant} "
                              f"rec_top10={rec_ids_list[:10]} overlap={overlap} "
                              f"P@{top_k}={p:.4f} R@{top_k}={r:.4f}")

    avg_precision = np.mean(precisions) if precisions else 0.0
    avg_recall = np.mean(recalls) if recalls else 0.0
    avg_ndcg = np.mean(ndcgs) if ndcgs else 0.0
    hrate = hit_rate_at_k(all_rec_lists, all_relevant, top_k)
    coverage = catalog_coverage(all_rec_lists, all_item_ids, top_k)

    diversity_scores = []
    sim_df = getattr(recommender, "item_similarity_df", None)
    if sim_df is None:
        sim_df = getattr(recommender, "item_cf_similarity_df", None)
    for uid, rec_list in all_rec_lists.items():
        diversity_scores.append(intra_list_diversity(rec_list, sim_df, top_k))
    avg_diversity = np.mean(diversity_scores) if diversity_scores else 0.0

    eval_logger.info(f"  → Prec={avg_precision:.4f} Rec={avg_recall:.4f} "
                     f"NDCG={avg_ndcg:.4f} HR={hrate:.4f} "
                     f"Cov={coverage:.4f} Div={avg_diversity:.4f} "
                     f"(空推荐: {empty_count}/{len(test_cases)})")

    return {
        "precision": avg_precision,
        "recall": avg_recall,
        "ndcg": avg_ndcg,
        "hit_rate": hrate,
        "coverage": coverage,
        "diversity": avg_diversity,
        "n_users": len(test_cases),
    }


# ═══════════════════════════════════════════════════════════════════════════
# 5. 图表生成
# ═══════════════════════════════════════════════════════════════════════════

def _generate_charts(results: dict):
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        from matplotlib import rcParams
    except ImportError:
        eval_logger.warning("matplotlib 未安装，跳过图表生成。")
        return

    rcParams['font.sans-serif'] = ['SimHei', 'Microsoft YaHei', 'WenQuanYi Micro Hei', 'DejaVu Sans']
    rcParams['axes.unicode_minus'] = False

    strategies = list(results.keys())
    k_vals = K_VALUES
    colors = ["#2196F3", "#4CAF50", "#FF9800", "#E91E63"]
    strategy_labels = {
        "hybrid": "混合推荐 (Hybrid)",
        "user_cf": "用户协同过滤 (UserCF)",
        "content_cf": "内容过滤 (ContentBase)",
        "item_cf": "物品协同过滤 (ItemCF)",
    }

    # --- 图 1: Precision@K, Recall@K, NDCG@K ---
    fig, axes = plt.subplots(1, 3, figsize=(18, 6))
    fig.suptitle("推荐算法性能对比 (80/20 Train-Test Split)", fontsize=16, fontweight='bold')

    for ax_idx, (metric, label) in enumerate(
            zip(["precision", "recall", "ndcg"],
                ["Precision@K", "Recall@K", "NDCG@K"])):
        ax = axes[ax_idx]
        x = np.arange(len(k_vals))
        width = 0.18
        offsets = np.arange(len(strategies)) - (len(strategies) - 1) / 2

        for s_idx, strat in enumerate(strategies):
            values = [results[strat].get(k, {}).get(metric, 0) for k in k_vals]
            bars = ax.bar(x + offsets[s_idx] * width, values, width,
                         label=strategy_labels.get(strat, strat),
                         color=colors[s_idx % len(colors)], alpha=0.85)
            for bar, val in zip(bars, values):
                if val > 0:
                    ax.text(bar.get_x() + bar.get_width() / 2,
                            bar.get_height() + 0.003,
                            f"{val:.3f}", ha='center', va='bottom', fontsize=7)

        ax.set_xlabel("K 值", fontsize=12)
        ax.set_ylabel(label, fontsize=12)
        ax.set_title(label, fontsize=13)
        ax.set_xticks(x)
        ax.set_xticklabels([f"K={k}" for k in k_vals])
        ax.legend(fontsize=8, loc='upper left')
        ax.grid(axis='y', alpha=0.3)

    plt.tight_layout(rect=[0, 0, 1, 0.93])
    p = os.path.join(CHART_DIR, "precision_recall_ndcg.png")
    plt.savefig(p, dpi=150, bbox_inches='tight')
    plt.close()
    eval_logger.info(f"图表已保存: {p}")

    # --- 图 2: Hit Rate@K 和 Coverage@K ---
    fig, axes = plt.subplots(1, 2, figsize=(14, 6))
    fig.suptitle("推荐覆盖率与命中率对比", fontsize=16, fontweight='bold')

    for ax_idx, metric in enumerate(["hit_rate", "coverage"]):
        ax = axes[ax_idx]
        x = np.arange(len(k_vals))
        width = 0.18
        offsets = np.arange(len(strategies)) - (len(strategies) - 1) / 2

        for s_idx, strat in enumerate(strategies):
            values = [results[strat].get(k, {}).get(metric, 0) for k in k_vals]
            bars = ax.bar(x + offsets[s_idx] * width, values, width,
                         label=strategy_labels.get(strat, strat),
                         color=colors[s_idx % len(colors)], alpha=0.85)
            for bar, val in zip(bars, values):
                if val > 0:
                    ax.text(bar.get_x() + bar.get_width() / 2,
                            bar.get_height() + 0.003,
                            f"{val:.3f}", ha='center', va='bottom', fontsize=7)

        ax.set_xlabel("K 值", fontsize=12)
        md = "Hit Rate@K" if metric == "hit_rate" else "Catalog Coverage@K"
        ax.set_ylabel(md, fontsize=12)
        ax.set_title(md, fontsize=13)
        ax.set_xticks(x)
        ax.set_xticklabels([f"K={k}" for k in k_vals])
        ax.legend(fontsize=9, loc='upper left')
        ax.grid(axis='y', alpha=0.3)

    plt.tight_layout(rect=[0, 0, 1, 0.93])
    p = os.path.join(CHART_DIR, "hit_rate_coverage.png")
    plt.savefig(p, dpi=150, bbox_inches='tight')
    plt.close()
    eval_logger.info(f"图表已保存: {p}")

    # --- 图 3: Diversity@K ---
    fig, ax = plt.subplots(figsize=(8, 6))
    fig.suptitle("推荐多样性对比 (Diversity@K)", fontsize=16, fontweight='bold')
    x = np.arange(len(k_vals))
    width = 0.18
    offsets = np.arange(len(strategies)) - (len(strategies) - 1) / 2
    for s_idx, strat in enumerate(strategies):
        values = [results[strat].get(k, {}).get("diversity", 0) for k in k_vals]
        bars = ax.bar(x + offsets[s_idx] * width, values, width,
                     label=strategy_labels.get(strat, strat),
                     color=colors[s_idx % len(colors)], alpha=0.85)
        for bar, val in zip(bars, values):
            if val > 0:
                ax.text(bar.get_x() + bar.get_width() / 2,
                        bar.get_height() + 0.003,
                        f"{val:.3f}", ha='center', va='bottom', fontsize=7)
    ax.set_xlabel("K 值", fontsize=12)
    ax.set_ylabel("Diversity@K (1 - Avg Similarity)", fontsize=12)
    ax.set_xticks(x)
    ax.set_xticklabels([f"K={k}" for k in k_vals])
    ax.legend(fontsize=9)
    ax.grid(axis='y', alpha=0.3)
    plt.tight_layout(rect=[0, 0, 1, 0.93])
    p = os.path.join(CHART_DIR, "diversity.png")
    plt.savefig(p, dpi=150, bbox_inches='tight')
    plt.close()
    eval_logger.info(f"图表已保存: {p}")

    # --- 图 4: Radar Chart (K=10) ---
    try:
        _generate_radar_chart(results, strategies, strategy_labels, colors, k_target=10)
    except Exception as e:
        eval_logger.warning(f"雷达图生成失败: {e}")

    # --- 图 5: Summary Table ---
    _generate_summary_table(results, strategies, strategy_labels)


def _generate_radar_chart(results, strategies, labels, colors, k_target=10):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    metrics = ["precision", "recall", "ndcg", "hit_rate", "coverage", "diversity"]
    metric_labels = ["Precision", "Recall", "NDCG", "Hit Rate", "Coverage", "Diversity"]
    N = len(metrics)
    angles = np.linspace(0, 2 * np.pi, N, endpoint=False).tolist()
    angles += angles[:1]

    fig, ax = plt.subplots(figsize=(8, 8), subplot_kw=dict(polar=True))
    fig.suptitle(f"推荐算法综合对比雷达图 (K={k_target})",
                 fontsize=15, fontweight='bold', y=1.02)

    for s_idx, strat in enumerate(strategies):
        values = [results[strat].get(k_target, {}).get(m, 0) for m in metrics]
        values += values[:1]
        ax.plot(angles, values, 'o-', linewidth=2,
                label=labels.get(strat, strat), color=colors[s_idx % len(colors)])
        ax.fill(angles, values, alpha=0.1, color=colors[s_idx % len(colors)])

    ax.set_xticks(angles[:-1])
    ax.set_xticklabels(metric_labels, fontsize=11)
    ax.legend(loc='upper right', bbox_to_anchor=(1.3, 1.1), fontsize=9)
    ax.set_ylim(0, 1)
    ax.grid(True, alpha=0.3)
    plt.tight_layout()
    p = os.path.join(CHART_DIR, "radar_chart.png")
    plt.savefig(p, dpi=150, bbox_inches='tight')
    plt.close()
    eval_logger.info(f"图表已保存: {p}")


def _generate_summary_table(results, strategies, labels):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    metrics = ["precision", "recall", "ndcg", "hit_rate", "coverage", "diversity"]
    metric_display = ["Precision", "Recall", "NDCG", "Hit Rate", "Coverage", "Diversity"]
    k_target = 10
    col_labels = [labels.get(s, s) for s in strategies]

    table_data = []
    best_cols = []
    for m in metrics:
        row_vals = [results[s].get(k_target, {}).get(m, 0) for s in strategies]
        best_idx = int(np.argmax(row_vals))
        best_cols.append(best_idx)
        table_data.append([f"{v:.4f}" for v in row_vals])

    fig, ax = plt.subplots(figsize=(14, 4.5))
    ax.axis('off')
    fig.suptitle(f"评估指标汇总 (K={k_target})", fontsize=14, fontweight='bold')

    table = ax.table(cellText=table_data, rowLabels=metric_display,
                     colLabels=col_labels, loc='center', cellLoc='center')
    table.auto_set_font_size(False)
    table.set_fontsize(11)
    table.scale(1.2, 1.8)

    for j in range(len(col_labels)):
        table[0, j].set_facecolor("#2196F3")
        table[0, j].set_text_props(color="white", fontweight='bold')
    for i in range(len(metric_display)):
        if best_cols[i] < len(col_labels):
            table[i + 1, best_cols[i]].set_facecolor("#C8E6C9")

    plt.tight_layout()
    p = os.path.join(CHART_DIR, "summary_table.png")
    plt.savefig(p, dpi=150, bbox_inches='tight')
    plt.close()
    eval_logger.info(f"图表已保存: {p}")


# ═══════════════════════════════════════════════════════════════════════════
# 6. 主入口
# ═══════════════════════════════════════════════════════════════════════════

def evaluate_leave_one_out():
    """
    完整的离线评估流程。
    评估 Hybrid / UserCF / ContentBase / ItemCF 四种策略，
    在 K=5, 10, 20 上分别计算各项指标。
    """
    eval_logger.info("=" * 70)
    eval_logger.info(f"离线评估开始于 {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    eval_logger.info("=" * 70)

    # Step 1: 加载数据
    data_result = _load_data()
    if data_result[0] is None:
        return None
    combined_df, qualifying_users, has_synthetic = data_result

    # Step 2: Train-Test Split
    train_df, test_cases = _train_test_split(combined_df, qualifying_users)
    # Use interaction data's item set as the catalog base
    all_item_ids = set(combined_df["post_id"].unique())

    if not test_cases:
        eval_logger.error("没有有效的测试用例，无法评估。")
        return None

    # Step 3: 训练推荐器
    eval_logger.info("正在训练评估用推荐器...")
    eval_rec = _train_eval_recommender(train_df)
    eval_logger.info("推荐器训练完成。")

    # Use the recommender's item universe as coverage denominator
    # (content-based may recommend posts outside the interaction set)
    if eval_rec.item_similarity_df is not None:
        rec_items = set(eval_rec.item_similarity_df.index)
        all_item_ids = all_item_ids | rec_items
    eval_logger.info(f"物品全集大小: {len(all_item_ids)} (交互: {len(set(combined_df['post_id'].unique()))})")

    # 检查用户矩阵中是否包含测试用户
    test_uids = [uid for uid, _ in test_cases]
    in_matrix = sum(1 for uid in test_uids if uid in eval_rec.user_item_matrix.index)
    eval_logger.info(f"测试用户在矩阵中: {in_matrix}/{len(test_uids)}")

    # Step 4: 评估各策略 × 各 K 值
    strategies = ["hybrid", "user_cf", "content_cf", "item_cf"]
    results = {}

    for strat in strategies:
        results[strat] = {}
        for k in K_VALUES:
            eval_logger.info(f"评估策略 [{strat}] @ K={k} ...")
            metrics = _evaluate_strategy(eval_rec, strat, test_cases, all_item_ids, k)
            results[strat][k] = metrics

    # Step 5: 打印报告
    _print_report(results, has_synthetic, len(qualifying_users), len(test_cases))

    # Step 6: 生成图表
    eval_logger.info("正在生成评估图表...")
    try:
        _generate_charts(results)
        eval_logger.info(f"所有图表已保存到: {CHART_DIR}")
    except Exception as e:
        eval_logger.error(f"图表生成失败: {e}")
        import traceback
        traceback.print_exc()

    eval_logger.info("=" * 70)
    eval_logger.info("离线评估完成。")
    eval_logger.info("=" * 70)

    hybrid_10 = results.get("hybrid", {}).get(10, {})
    return {
        "precision_at_k": hybrid_10.get("precision", 0),
        "recall_at_k": hybrid_10.get("recall", 0),
        "hits": int(hybrid_10.get("hit_rate", 0) * len(test_cases)),
        "evaluated": len(test_cases),
        "full_results": results,
    }


def _print_report(results: dict, has_synthetic: bool, n_qualifying: int, n_evaluated: int):
    sl = {
        "hybrid": "混合推荐 (Hybrid)",
        "user_cf": "用户协同过滤 (UserCF)",
        "content_cf": "内容过滤 (ContentBase)",
        "item_cf": "物品协同过滤 (ItemCF)",
    }
    strat_order = ["hybrid", "user_cf", "content_cf", "item_cf"]

    eval_logger.info("")
    eval_logger.info("=" * 70)
    eval_logger.info("                      离 线 评 估 报 告")
    eval_logger.info("=" * 70)
    eval_logger.info(f"  评估方法       : 80/20 Train-Test Split")
    eval_logger.info(f"  符合条件用户数 : {n_qualifying}")
    eval_logger.info(f"  已评估用户数   : {n_evaluated}")
    if has_synthetic:
        eval_logger.info(f"  [注意] 使用了合成数据补充")
    eval_logger.info("")

    for k in K_VALUES:
        eval_logger.info(f"--- K = {k} ---")
        eval_logger.info(f"  {'策略':<28} {'Prec':>8} {'Rec':>8} {'NDCG':>8} {'HR':>8} {'Cov':>8} {'Div':>8}")
        eval_logger.info(f"  {'─' * 84}")
        for strat in strat_order:
            m = results[strat].get(k, {})
            name = sl.get(strat, strat)
            eval_logger.info(f"  {name:<28} "
                             f"{m.get('precision', 0):>8.4f} "
                             f"{m.get('recall', 0):>8.4f} "
                             f"{m.get('ndcg', 0):>8.4f} "
                             f"{m.get('hit_rate', 0):>8.4f} "
                             f"{m.get('coverage', 0):>8.4f} "
                             f"{m.get('diversity', 0):>8.4f}")
        eval_logger.info("")


if __name__ == "__main__":
    evaluate_leave_one_out()
