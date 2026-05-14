"""
系统级端到端推荐评估脚本
==========================
通过 HTTP API 完成完整推荐流程的端到端测试，评估的是：
  Java 标签权重 + ES 内容推荐 + Python CF + 热度加分 + 全局惩罚 + 关注注入 + 冷启动
  的综合效果。

对照组设计:
  - Group A (活跃用户): 交互 10+ 帖子，Leave-One-Out 评估推荐准确性
  - Group B (新用户):   仅注册，0 交互，验证冷启动效果
  - Group C (轻度用户): 交互 2~3 帖子，验证少数据场景（不参与Precision/Recall）

用法:
    python eval_system.py [--host localhost] [--port 8080]
"""

import os
import sys
import json
import time
import logging
import argparse
import requests
import numpy as np
import pandas as pd
from datetime import datetime
from collections import defaultdict, Counter

# ---------------------------------------------------------------------------
# 日志配置
# ---------------------------------------------------------------------------
base_dir = os.path.dirname(os.path.abspath(__file__))
log_dir = os.path.join(base_dir, "logs")
os.makedirs(log_dir, exist_ok=True)

CHART_DIR = os.path.join(log_dir, "system_evaluation_charts")
os.makedirs(CHART_DIR, exist_ok=True)

sys_logger = logging.getLogger("SystemEval")
sys_logger.setLevel(logging.DEBUG)
sys_logger.propagate = False

_fh = logging.FileHandler(os.path.join(log_dir, "system_evaluation.log"), encoding="utf-8")
_fh.setLevel(logging.DEBUG)
_fh.setFormatter(logging.Formatter("%(asctime)s [%(levelname)s] %(message)s"))
sys_logger.addHandler(_fh)

_sh = logging.StreamHandler()
_sh.setLevel(logging.INFO)
_sh.setFormatter(logging.Formatter("%(asctime)s [%(levelname)s] %(message)s"))
sys_logger.addHandler(_sh)

# ---------------------------------------------------------------------------
# 全局常量
# ---------------------------------------------------------------------------
PREFIX = "eval_sys_"           # 测试用户名前缀，方便批量清理
PASSWORD = "EvalTest123456"    # 所有测试用户统一密码

# 各组用户数
N_ACTIVE_USERS = 50            # Group A (Leave-One-Out 评估)
N_NEW_USERS = 10               # Group B (冷启动)
N_LIGHT_USERS = 10             # Group C (标签匹配, 不参与Precision/Recall)

RECOMMEND_SIZE = 20            # 每个用户获取的推荐数量


# ═══════════════════════════════════════════════════════════════════════════
# 1. HTTP 客户端封装
# ═══════════════════════════════════════════════════════════════════════════

class APIError(Exception):
    """API 调用异常"""
    def __init__(self, status_code, message):
        self.status_code = status_code
        super().__init__(f"HTTP {status_code}: {message}")


class GoodshareClient:
    """封装所有 API 调用"""

    def __init__(self, base_url: str, timeout: int = 15):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.session = requests.Session()
        self.session.headers.update({"Content-Type": "application/json"})

    def _request(self, method: str, path: str, token: str = None, **kwargs) -> dict:
        url = f"{self.base_url}{path}"
        headers = {}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        try:
            resp = self.session.request(method, url, headers=headers, timeout=self.timeout, **kwargs)
            if resp.status_code >= 400:
                raise APIError(resp.status_code, resp.text[:200])
            if not resp.content or resp.content.strip() == b"":
                return {}
            try:
                return resp.json()
            except (ValueError, requests.exceptions.JSONDecodeError):
                return {"_text": resp.text}
        except requests.exceptions.ConnectionError as e:
            raise APIError(0, f"连接失败: {e}")
        except requests.exceptions.Timeout:
            raise APIError(0, "请求超时")

    # ---- 认证 ----
    def register(self, username: str, password: str, email: str) -> dict:
        return self._request("POST", "/api/auth/register", json={
            "username": username, "password": password,
            "email": email, "captcha": "123456",
        })

    def login(self, username: str, password: str) -> dict:
        return self._request("POST", "/api/auth/login", json={
            "username": username, "password": password,
        })

    # ---- 帖子 ----
    def get_posts(self, page: int = 1, size: int = 50, tag: str = None) -> dict:
        params = {"page": page, "size": size}
        if tag:
            params["tag"] = tag
        return self._request("GET", "/api/posts", params=params)

    def view_post(self, post_id: int, token: str = None) -> dict:
        return self._request("POST", f"/api/posts/{post_id}/view", token=token)

    # ---- 点赞 ----
    def like_post(self, post_id: int, token: str) -> dict:
        return self._request("POST", f"/api/posts/{post_id}/likes", token=token)

    # ---- 收藏 ----
    def favorite_post(self, post_id: int, token: str) -> dict:
        return self._request("POST", f"/api/favorites/{post_id}", token=token)

    # ---- 评论 ----
    def comment_post(self, post_id: int, content: str, token: str) -> dict:
        return self._request("POST", f"/api/posts/{post_id}/comments",
                             json={"content": content}, token=token)

    # ---- 推荐（正常模式，过滤已浏览） ----
    def get_recommendations(self, user_id: int, size: int = 20, token: str = None) -> list:
        data = self._request("GET", "/api/recommendations",
                             params={"user_id": user_id, "size": size}, token=token)
        return data if isinstance(data, list) else data.get("data", data.get("content", []))

    # ---- 推荐（评估专用，不过滤已浏览帖子） ----
    def get_recommendations_eval(self, user_id: int, size: int = 20, token: str = None) -> list:
        data = self._request("GET", "/api/recommendations/eval",
                             params={"user_id": user_id, "size": size}, token=token)
        return data if isinstance(data, list) else data.get("data", data.get("content", []))

    # ---- 标签 ----
    def get_tags(self) -> list:
        return self._request("GET", "/api/tags")

    # ---- 健康检查 ----
    def health(self) -> bool:
        try:
            self._request("GET", "/api/posts?page=1&size=1")
            return True
        except Exception:
            return False

    # ---- 触发 Python 推荐服务重训 ----
    def trigger_retrain(self, rec_service_url: str = "http://localhost:5000") -> bool:
        """调用 Python 推荐服务的 /retrain 端点，强制重训模型"""
        try:
            resp = requests.get(f"{rec_service_url}/retrain", timeout=120)
            if resp.status_code == 200:
                data = resp.json()
                if data.get("status") == "ok":
                    sys_logger.info("  ✓ Python 推荐模型重训完成")
                    return True
                else:
                    sys_logger.warning(f"  重训返回错误: {data}")
                    return False
            else:
                sys_logger.warning(f"  重训请求失败: HTTP {resp.status_code}")
                return False
        except Exception as e:
            sys_logger.warning(f"  调用重训接口失败: {e}")
            return False


# ═══════════════════════════════════════════════════════════════════════════
# 2. 数据准备
# ═══════════════════════════════════════════════════════════════════════════

def _fetch_all_posts(client: GoodshareClient) -> list:
    all_posts = []
    page = 1
    page_size = 100
    while True:
        data = client.get_posts(page=page, size=page_size)
        records = data.get("records", [])
        if not records:
            break
        all_posts.extend(records)
        total = data.get("total", 0)
        if len(all_posts) >= total:
            break
        page += 1
        time.sleep(0.1)
    sys_logger.info(f"从服务器获取了 {len(all_posts)} 个帖子")
    return all_posts


def _fetch_all_tags(client: GoodshareClient) -> list:
    tags = client.get_tags()
    sys_logger.info(f"从服务器获取了 {len(tags)} 个标签")
    return tags


def _build_post_tag_map(posts: list) -> dict:
    post_tag_map = {}
    for post in posts:
        pid = post.get("id")
        tags = post.get("tags", [])
        if isinstance(tags, list):
            tag_names = []
            for t in tags:
                if isinstance(t, dict):
                    tag_names.append(t.get("name", ""))
                elif isinstance(t, str):
                    tag_names.append(t)
            post_tag_map[pid] = tag_names
        else:
            post_tag_map[pid] = []
    return post_tag_map


def _build_tag_post_map(post_tag_map: dict) -> dict:
    tag_post_map = defaultdict(list)
    for pid, tags in post_tag_map.items():
        for tag in tags:
            tag_post_map[tag].append(pid)
    return dict(tag_post_map)


# ═══════════════════════════════════════════════════════════════════════════
# 3. 测试用户生命周期
# ═══════════════════════════════════════════════════════════════════════════

def _register_and_login(client: GoodshareClient, username: str) -> dict:
    email = f"{username}@eval.test"
    try:
        client.register(username, PASSWORD, email)
    except APIError as e:
        if "already" in str(e).lower():
            sys_logger.debug(f"用户 {username} 已存在，跳过注册")
        else:
            raise
    resp = client.login(username, PASSWORD)
    token = resp.get("accessToken", resp.get("access_token", ""))
    return {"token": token, "username": username}


def _build_user_profiles(client: GoodshareClient, users: list,
                         tag_post_map: dict, all_tags: list,
                         rng: np.random.Generator,
                         skip_phase2: bool = False):
    """为活跃用户和轻度用户建立交互画像。

    活跃用户 (Group A) 采用 Leave-One-Out 高交互切分：
      - Phase 1 (训练): 从候选池取 80% 的帖子进行浏览/点赞/收藏/评论
      - Phase 2 (测试): 从候选池剩余 20% 的帖子中取少量做高交互（点赞+收藏）
        这些帖子作为 ground truth，用于评估推荐质量

    当 skip_phase2=True 时，仅执行 Phase 1 交互，Phase 2 帖子会被选中但不执行交互。
    Phase 2 的选择结果保存在 user["phase2_posts_pending"] 中，后续调用
    _execute_phase2_interactions() 来执行。

    轻度用户 (Group C): 交互量太少，不做切分，全部作为训练数据
    """
    comment_texts = [
        "说得好！", "非常有用", "学到了", "感谢分享", "太棒了！",
        "收藏了", "赞！", "写得真好", "有同感", "不错不错",
        "太有帮助了", "种草了", "感谢推荐", "真心推荐", "涨知识了"
    ]

    for user in users:
        if user["group"] == "B":
            continue

        token = user["token"]
        preferred_tags = user["preferred_tags"]
        group = user["group"]

        candidate_posts = []
        for tag in preferred_tags:
            candidate_posts.extend(tag_post_map.get(tag, []))
        candidate_posts = list(set(candidate_posts))

        if not candidate_posts:
            all_pids = list(tag_post_map.keys())
            candidate_posts = rng.choice(all_pids, size=min(20, len(all_pids)), replace=False).tolist()

        if group == "A":
            # ---- Leave-One-Out 高交互切分 ----
            # 需要至少 10 篇候选帖子才能做切分
            if len(candidate_posts) >= 10:
                rng.shuffle(candidate_posts)
                # 留出 30% 帖子作为 Phase 2 候选池（最少 10 篇，最多 30 篇）
                n_phase2_pool = max(10, min(30, int(len(candidate_posts) * 0.3)))
                phase2_pool = candidate_posts[:n_phase2_pool]
                phase1_posts = candidate_posts[n_phase2_pool:]
            else:
                # 候选太少，无法切分，全部作为训练
                phase1_posts = candidate_posts
                phase2_pool = []

            # ---- Phase 1: 训练交互 ----
            n_view = max(5, int(rng.integers(8, min(len(phase1_posts) + 1, 16))))
            n_like = int(rng.integers(3, min(9, n_view + 1)))
            n_fav = int(rng.integers(1, min(4, n_like + 1)))
            n_comment = int(rng.integers(1, 3))

            view_count = min(n_view, len(phase1_posts))
            viewed_posts = rng.choice(phase1_posts, size=view_count, replace=False).tolist() if view_count > 0 else []
            user["viewed_posts"] = set(viewed_posts)
            for pid in viewed_posts:
                try:
                    client.view_post(pid, token=token)
                except APIError:
                    pass
                time.sleep(0.05)

            like_count = min(n_like, len(viewed_posts))
            liked_posts = rng.choice(viewed_posts, size=like_count, replace=False).tolist() if like_count > 0 else []
            user["liked_posts"] = set(liked_posts)
            for pid in liked_posts:
                try:
                    client.like_post(pid, token=token)
                except APIError:
                    pass
                time.sleep(0.05)

            fav_count = min(n_fav, len(viewed_posts))
            fav_posts = rng.choice(viewed_posts, size=fav_count, replace=False).tolist() if fav_count > 0 else []
            user["favorited_posts"] = set(fav_posts)
            for pid in fav_posts:
                try:
                    client.favorite_post(pid, token=token)
                except APIError:
                    pass
                time.sleep(0.05)

            comment_count = min(n_comment, len(viewed_posts))
            comment_posts = rng.choice(viewed_posts, size=comment_count, replace=False).tolist() if comment_count > 0 else []
            for pid in comment_posts:
                try:
                    client.comment_post(pid, rng.choice(comment_texts), token=token)
                except APIError:
                    pass
                time.sleep(0.05)

            # 保存 Phase 1 汇总
            user["phase1_liked"] = set(liked_posts)
            user["phase1_favorited"] = set(fav_posts)
            user["phase1_viewed"] = set(viewed_posts)

            # ---- Phase 2: 测试交互（留出） ----
            # 从 Phase 2 候选池中生成真实、多样化的用户行为
            # 浏览率 ~50-80%，点赞率 ~20-40%，收藏率 ~10-20%（均相对于浏览数）
            if phase2_pool:
                # 从候选池中随机选择浏览的帖子（50%~80% 的候选池）
                view_ratio = rng.uniform(0.5, 0.8)
                n_phase2_view = max(3, min(len(phase2_pool), int(len(phase2_pool) * view_ratio)))
                phase2_viewed = rng.choice(phase2_pool, size=n_phase2_view, replace=False).tolist()

                # 从浏览的帖子中随机选择点赞（20%~40%，至少 1 篇）
                like_ratio = rng.uniform(0.2, 0.4)
                n_phase2_like = max(1, min(len(phase2_viewed), int(len(phase2_viewed) * like_ratio)))
                phase2_liked = rng.choice(phase2_viewed, size=n_phase2_like, replace=False).tolist()

                # 从点赞的帖子中随机选择收藏（30%~60%，至少 1 篇）
                fav_ratio = rng.uniform(0.3, 0.6)
                n_phase2_fav = max(1, min(len(phase2_liked), int(len(phase2_liked) * fav_ratio)))
                phase2_fav = rng.choice(phase2_liked, size=n_phase2_fav, replace=False).tolist()

                if skip_phase2:
                    # 仅保存 Phase 2 选择结果，不执行交互
                    user["phase2_posts_pending"] = {
                        "phase2_viewed": phase2_viewed,
                        "phase2_gt": phase2_liked,
                        "phase2_fav": phase2_fav,
                    }
                    user["test_relevant"] = set()
                    user["phase2_viewed"] = set()
                    user["phase2_liked"] = set()
                    user["phase2_favorited"] = set()
                else:
                    # 立即执行 Phase 2 交互
                    for pid in phase2_viewed:
                        try:
                            client.view_post(pid, token=token)
                        except APIError:
                            pass
                        time.sleep(0.05)
                    for pid in phase2_liked:
                        try:
                            client.like_post(pid, token=token)
                        except APIError:
                            pass
                        time.sleep(0.05)
                    for pid in phase2_fav:
                        try:
                            client.favorite_post(pid, token=token)
                        except APIError:
                            pass
                        time.sleep(0.05)

                    # ground truth = 点赞 ∪ 收藏（通常收藏 ⊂ 点赞，所以 = 点赞集）
                    user["test_relevant"] = set(phase2_liked) | set(phase2_fav)
                    user["phase2_viewed"] = set(phase2_viewed)
                    user["phase2_liked"] = set(phase2_liked)
                    user["phase2_favorited"] = set(phase2_fav)
            else:
                user["test_relevant"] = set()
                user["phase2_viewed"] = set()
                user["phase2_liked"] = set()
                user["phase2_favorited"] = set()

            sys_logger.info(f"  用户 {user['username']} ({group}): "
                            f"Phase1 浏览={len(viewed_posts)}, 点赞={len(liked_posts)}, "
                            f"收藏={fav_count} | "
                            f"Phase2 候选池={len(phase2_pool) if phase2_pool else 0}, "
                            f"浏览={n_phase2_view if phase2_pool else 0}, "
                            f"点赞={n_phase2_like if phase2_pool else 0}, "
                            f"收藏={n_phase2_fav if phase2_pool else 0}, "
                            f"Ground Truth={len(user['test_relevant'])}")

        else:
            # ---- 轻度用户 (Group C): 不做切分 ----
            n_view = int(rng.integers(2, 4))
            n_like = int(rng.integers(0, 2))
            n_fav = 0
            n_comment = 0

            view_count = min(n_view, len(candidate_posts))
            viewed_posts = rng.choice(candidate_posts, size=view_count, replace=False).tolist() if view_count > 0 else []
            user["viewed_posts"] = set(viewed_posts)
            user["phase1_viewed"] = set(viewed_posts)
            for pid in viewed_posts:
                try:
                    client.view_post(pid, token=token)
                except APIError:
                    pass
                time.sleep(0.05)

            like_count = min(n_like, len(viewed_posts))
            liked_posts = rng.choice(viewed_posts, size=like_count, replace=False).tolist() if like_count > 0 else []
            user["liked_posts"] = set(liked_posts)
            user["phase1_liked"] = set(liked_posts)
            for pid in liked_posts:
                try:
                    client.like_post(pid, token=token)
                except APIError:
                    pass
                time.sleep(0.05)

            fav_count = min(n_fav, len(viewed_posts))
            fav_posts = rng.choice(viewed_posts, size=fav_count, replace=False).tolist() if fav_count > 0 else []
            user["favorited_posts"] = set(fav_posts)
            user["phase1_favorited"] = set(fav_posts)
            for pid in fav_posts:
                try:
                    client.favorite_post(pid, token=token)
                except APIError:
                    pass
                time.sleep(0.05)

            user["test_relevant"] = set()  # 轻度用户不做 LOO 评估
            user["phase2_viewed"] = set()
            user["phase2_liked"] = set()
            user["phase2_favorited"] = set()

            sys_logger.info(f"  用户 {user['username']} ({group}): "
                            f"浏览={len(viewed_posts)}, 点赞={len(liked_posts)}, "
                            f"收藏={fav_count}, 评论={comment_count}")


def _execute_phase2_interactions(client: GoodshareClient, users: list):
    """在获取推荐之后，执行之前跳过的 Phase 2 交互（ground truth）。

    此函数从 user["phase2_posts_pending"] 中读取待执行的 Phase 2 帖子选择，
    执行浏览/点赞/收藏操作，并设置 test_relevant 等字段供评估使用。
    """
    for user in users:
        pending = user.get("phase2_posts_pending")
        if not pending:
            continue

        token = user["token"]
        phase2_viewed = pending["phase2_viewed"]
        phase2_gt = pending["phase2_gt"]
        phase2_fav = pending["phase2_fav"]

        # 执行 Phase 2 交互
        for pid in phase2_viewed:
            try:
                client.view_post(pid, token=token)
            except APIError:
                pass
            time.sleep(0.05)
        for pid in phase2_gt:
            try:
                client.like_post(pid, token=token)
            except APIError:
                pass
            time.sleep(0.05)
        for pid in phase2_fav:
            try:
                client.favorite_post(pid, token=token)
            except APIError:
                pass
            time.sleep(0.05)

        # 设置评估字段
        user["test_relevant"] = set(phase2_gt) | set(phase2_fav)
        user["phase2_viewed"] = set(phase2_viewed)
        user["phase2_liked"] = set(phase2_gt)
        user["phase2_favorited"] = set(phase2_fav)

        sys_logger.info(f"  用户 {user['username']} Phase2 执行: "
                        f"浏览={len(phase2_viewed)}, 点赞={len(phase2_gt)}, "
                        f"收藏={len(phase2_fav)}, "
                        f"Ground Truth={len(user['test_relevant'])}")


# ═══════════════════════════════════════════════════════════════════════════
# 4. 获取推荐并计算指标
# ═══════════════════════════════════════════════════════════════════════════

def _fetch_recommendations(client: GoodshareClient, users: list):
    """获取推荐结果。Group A 活跃用户使用评估专用接口（不过滤已浏览），其他用正常接口。"""
    fallback_token = None
    for u in users:
        if u.get("token"):
            fallback_token = u["token"]
            break

    for user in users:
        uid = user.get("user_id")
        if uid is None:
            sys_logger.warning(f"用户 {user['username']} 没有 user_id，跳过推荐")
            user["recommendations"] = []
            continue
        token = user.get("token") or fallback_token
        try:
            if user.get("group") == "A":
                # Group A: 使用评估专用接口，不过滤已浏览帖子
                recs = client.get_recommendations_eval(uid, size=RECOMMEND_SIZE, token=token)
                # 按 recommendScore 降序重排，消除 Java 端 shuffle 的影响
                if isinstance(recs, list) and recs:
                    recs = sorted(
                        recs,
                        key=lambda p: p.get("recommendScore", 0) if isinstance(p, dict) else 0,
                        reverse=True
                    )
            else:
                # Group B/C: 使用正常接口
                recs = client.get_recommendations(uid, size=RECOMMEND_SIZE, token=token)
            user["recommendations"] = recs if isinstance(recs, list) else []
        except APIError as e:
            sys_logger.warning(f"获取推荐失败 ({user['username']}): {e}")
            user["recommendations"] = []
        time.sleep(0.2)


def _compute_tag_coverage(recs: list, all_tags: set, post_tag_map: dict) -> float:
    """推荐列表覆盖了多少不同标签"""
    if not recs or not all_tags:
        return 0.0
    rec_tags = set()
    for post in recs:
        pid = post.get("id") if isinstance(post, dict) else post
        rec_tags.update(post_tag_map.get(pid, []))
    return len(rec_tags) / len(all_tags) if all_tags else 0.0


def _compute_diversity(recs: list, post_tag_map: dict) -> tuple:
    """多样性：Shannon 熵 + 不同标签数 / K"""
    if not recs:
        return 0.0, 0.0
    tag_counter = Counter()
    for post in recs:
        pid = post.get("id") if isinstance(post, dict) else post
        for t in post_tag_map.get(pid, []):
            tag_counter[t] += 1
    total = sum(tag_counter.values())
    if total == 0:
        return 0.0, 0.0
    entropy = 0.0
    for count in tag_counter.values():
        p = count / total
        if p > 0:
            entropy -= p * np.log2(p)
    max_entropy = np.log2(len(tag_counter)) if len(tag_counter) > 1 else 1.0
    normalized_entropy = entropy / max_entropy if max_entropy > 0 else 0.0
    unique_ratio = len(tag_counter) / len(recs)
    return normalized_entropy, unique_ratio


def _compute_tag_consistency(recs: list, preferred_tags: list, post_tag_map: dict) -> float:
    """Jaccard 相似度：推荐内容标签集 vs 用户偏好标签集"""
    if not recs or not preferred_tags:
        return 0.0
    rec_tags = set()
    for post in recs:
        pid = post.get("id") if isinstance(post, dict) else post
        rec_tags.update(post_tag_map.get(pid, []))
    preferred_set = set(preferred_tags)
    intersection = rec_tags & preferred_set
    union = rec_tags | preferred_set
    return len(intersection) / len(union) if union else 0.0


def _compute_preference_match_rate(recs: list, preferred_tags: list, post_tag_map: dict) -> float:
    """推荐帖子中包含偏好标签的比例"""
    if not recs or not preferred_tags:
        return 0.0
    hits = 0
    for post in recs:
        pid = post.get("id") if isinstance(post, dict) else post
        tags = post_tag_map.get(pid, [])
        if any(t in preferred_tags for t in tags):
            hits += 1
    return hits / len(recs)


def _compute_precision_recall(rec_ids: list, relevant: set, k: int) -> dict:
    """计算 Precision@K, Recall@K, (rec_ids 为 post_id 列表)"""
    rec_set = set(rec_ids[:k])

    precision = len(rec_set & relevant) / k if k > 0 else 0.0
    recall = len(rec_set & relevant) / len(relevant) if relevant else 0.0

    return {"precision": precision, "recall": recall}


def _compute_offline_eval_results(user_metrics: list = None) -> dict:
    """
    计算 Python 各算法在 Group A 测试用户上的推荐质量指标，与 Java 系统做公平对比。

    公平性保证：
    - 使用与 Java 系统**完全相同的测试用户** (Group A 的 eval 用户)
    - 使用**完全相同的 Ground Truth** (Phase 2 高交互帖子)
    - Python 算法在 Phase 1 交互数据上训练，与 Java 系统使用相同的训练数据

    返回: {strategy: {k: {precision, recall, hit_rate}}}
    """
    try:
        from sqlalchemy import create_engine, text
        from recommender import HybridRecommender
        import database as db_module
        import recommender as rec_module

        DB_URL = os.getenv("DB_URL", "mysql+pymysql://root:123456@localhost:3306/goodshare")
        engine = create_engine(DB_URL, pool_pre_ping=True)

        if user_metrics is None:
            sys_logger.warning("未传入 user_metrics，跳过离线评估对比")
            return {}

        # 筛选 Group A 活跃用户（有 Phase 1 交互且有 Ground Truth）
        group_a = [u for u in user_metrics
                    if u.get("group") == "A"
                    and u.get("user_id")
                    and u.get("test_relevant")]
        if len(group_a) < 5:
            sys_logger.warning(f"Group A 有效用户不足 ({len(group_a)})，跳过离线评估对比")
            return {}

        sys_logger.info(f"  离线评估: 使用 {len(group_a)} 个 Group A 测试用户")

        # ----------------------------------------------------------------
        # 构建训练集：仅包含 Phase 1 交互（排除 Phase 2 帖子），
        # 与 Java 系统在获取推荐时使用的数据完全一致
        # ----------------------------------------------------------------
        # 收集所有 Group A 用户的 Phase 2 帖子（需要从训练集中排除）
        phase2_post_ids = set()
        test_cases = []  # [(user_id, relevant_set)]

        for u in group_a:
            phase2 = u.get("phase2_liked", set()) | u.get("phase2_favorited", set())
            phase2_post_ids.update(phase2)
            test_cases.append((u["user_id"], u["test_relevant"]))

        # 从数据库读取所有真实交互
        real_df = db_module.fetch_interactions()
        if real_df.empty:
            sys_logger.warning("数据库中无交互数据，跳过离线评估对比")
            return {}

        # 排除 eval 测试用户的交互（他们的数据是合成的，不参与训练）
        eval_user_ids = set(u["user_id"] for u in user_metrics if u.get("user_id"))
        real_df_filtered = real_df[~real_df["user_id"].isin(eval_user_ids)]

        # 获取每个 Group A 用户的 Phase 1 交互（从数据库读取）
        with engine.connect() as conn:
            phase1_records = []
            for u in group_a:
                uid = u["user_id"]
                phase2_ids = u.get("phase2_liked", set()) | u.get("phase2_favorited", set())
                phase2_view_ids = u.get("phase2_viewed", set())
                all_phase2 = phase2_ids | phase2_view_ids

                # 读取该用户的所有交互，排除 Phase 2 帖子
                if all_phase2:
                    # 构建排除列表的 SQL
                    exclude_str = ",".join(str(int(p)) for p in all_phase2)
                    rows = conn.execute(text(f"""
                        SELECT user_id, post_id, score FROM (
                            SELECT user_id, post_id,
                                   (CASE WHEN src='view' THEN 1 WHEN src='like' THEN 3
                                         WHEN src='comment' THEN 5 WHEN src='favorite' THEN 5 END) as score
                            FROM (
                                SELECT user_id, post_id, 'view' as src FROM post_views WHERE user_id = :uid AND post_id NOT IN ({exclude_str})
                                UNION ALL
                                SELECT user_id, post_id, 'like' as src FROM post_likes WHERE user_id = :uid AND post_id NOT IN ({exclude_str})
                                UNION ALL
                                SELECT user_id, post_id, 'comment' as src FROM comments WHERE user_id = :uid AND post_id NOT IN ({exclude_str})
                                UNION ALL
                                SELECT user_id, post_id, 'favorite' as src FROM favorites WHERE user_id = :uid AND post_id NOT IN ({exclude_str})
                            ) raw
                        ) scored
                    """), {"uid": uid}).fetchall()
                else:
                    rows = []

                for row in rows:
                    phase1_records.append({
                        "user_id": int(row[0]),
                        "post_id": int(row[1]),
                        "score": int(row[2]),
                    })

        # 合并：真实用户交互 + Group A 用户的 Phase 1 交互
        if phase1_records:
            phase1_df = pd.DataFrame(phase1_records)
            phase1_df = phase1_df.groupby(["user_id", "post_id"])["score"].sum().reset_index()
            train_df = pd.concat([real_df_filtered, phase1_df], ignore_index=True)
        else:
            train_df = real_df_filtered

        # 去重聚合
        train_df = train_df.groupby(["user_id", "post_id"])["score"].sum().reset_index()
        sys_logger.info(f"  训练集: {len(train_df)} 条交互, "
                        f"{train_df['user_id'].nunique()} 个用户, "
                        f"{train_df['post_id'].nunique()} 个帖子")

        # Monkey-patch 训练
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

        # 检查测试用户是否在训练矩阵中
        in_matrix = sum(1 for uid, _ in test_cases if uid in eval_rec.user_item_matrix.index)
        sys_logger.info(f"  测试用户在 Python 矩阵中: {in_matrix}/{len(test_cases)}")

        # ----------------------------------------------------------------
        # 评估各策略
        # ----------------------------------------------------------------
        strategies = ["hybrid", "user_cf", "content_base", "item_cf"]
        k_values = [5, 10, 20]
        strategy_map = {
            "hybrid": lambda uid, k: eval_rec.recommend(uid, top_k=k),
            "user_cf": lambda uid, k: eval_rec._recommend_user_cf(uid, top_k=k),
            "content_base": lambda uid, k: eval_rec._recommend_content_based(uid, top_k=k),
            "item_cf": lambda uid, k: eval_rec._recommend_item_cf(uid, top_k=k),
        }

        results = {}
        for strat in strategies:
            results[strat] = {}
            recommend_fn = strategy_map[strat]
            for k in k_values:
                precisions, recalls, hit_flags = [], [], []

                for uid, relevant in test_cases:
                    try:
                        recs = recommend_fn(uid, k)
                    except Exception:
                        recs = []

                    rec_ids_list = [r["post_id"] for r in recs]
                    m = _compute_precision_recall(rec_ids_list, relevant, k)
                    precisions.append(m["precision"])
                    recalls.append(m["recall"])
                    hit_flags.append(1 if set(rec_ids_list[:k]) & relevant else 0)

                results[strat][k] = {
                    "precision": np.mean(precisions) if precisions else 0.0,
                    "recall": np.mean(recalls) if recalls else 0.0,
                    "hit_rate": np.mean(hit_flags) if hit_flags else 0.0,
                }

        # 打印结果
        for strat in strategies:
            for k in k_values:
                r = results[strat][k]
                sys_logger.info(f"  [{strat}] K={k}: "
                                f"P={r['precision']:.4f} R={r['recall']:.4f} HR={r['hit_rate']:.4f}")

        return results

    except Exception as e:
        sys_logger.warning(f"离线评估对比失败: {e}")
        import traceback
        traceback.print_exc()
        return {}


def _diversity_from_rec_ids(rec_ids: list, post_tag_map: dict) -> float:
    """从 post_id 列表计算 Shannon 熵归一化多样性"""
    tag_counter = Counter()
    for pid in rec_ids:
        for t in post_tag_map.get(pid, []):
            tag_counter[t] += 1
    total = sum(tag_counter.values())
    if total == 0 or len(tag_counter) <= 1:
        return 0.0
    entropy = -sum((c / total) * np.log2(c / total) for c in tag_counter.values())
    max_ent = np.log2(len(tag_counter))
    return entropy / max_ent if max_ent > 0 else 0.0


# ═══════════════════════════════════════════════════════════════════════════
# 5. Python 端推荐对比（与 Java 系统推荐的公平对比）
# ═══════════════════════════════════════════════════════════════════════════

def _get_python_recommendations_for_real_users(sample_size: int = 50) -> dict:
    """
    从数据库读取真实活跃用户的交互数据，用 Python 端各算法生成推荐，
    与 Java 系统推荐做价值对比。
    返回: {strategy: [{user_id, rec_ids, preferred_tags}]}
    """
    try:
        from sqlalchemy import create_engine, text
        DB_URL = os.getenv("DB_URL", "mysql+pymysql://root:123456@localhost:3306/goodshare")
        engine = create_engine(DB_URL, pool_pre_ping=True)

        with engine.connect() as conn:
            rows = conn.execute(text("""
                SELECT u.id, COUNT(*) as cnt
                FROM users u
                JOIN (
                    SELECT user_id FROM post_views
                    UNION ALL SELECT user_id FROM post_likes
                    UNION ALL SELECT user_id FROM comments
                    UNION ALL SELECT user_id FROM favorites
                ) all_int ON u.id = all_int.user_id
                WHERE u.username NOT LIKE :prefix
                GROUP BY u.id
                HAVING cnt >= 5
                ORDER BY cnt DESC
                LIMIT :limit
            """), {"prefix": f"{PREFIX}%", "limit": sample_size}).fetchall()

        if not rows:
            sys_logger.warning("数据库中没有足够活跃的真实用户，跳过 Python 对比")
            return {}

        user_ids = [r[0] for r in rows]
        sys_logger.info(f"  找到 {len(user_ids)} 个真实活跃用户用于 Python 算法对比")

        import database as db_module
        from recommender import HybridRecommender

        eval_rec = HybridRecommender()
        eval_rec.train()

        # 获取每个用户的标签偏好
        contents_df = db_module.fetch_post_contents()
        post_tag_map_db = {}
        if not contents_df.empty and 'tags' in contents_df.columns:
            for _, row in contents_df.iterrows():
                if row['tags'] and str(row['tags']).strip():
                    post_tag_map_db[row['post_id']] = str(row['tags']).strip().split()

        interactions_df = db_module.fetch_interactions()
        user_tag_map = {}
        for uid in user_ids:
            user_interactions = interactions_df[interactions_df['user_id'] == uid]
            tag_counter = Counter()
            for _, row in user_interactions.iterrows():
                for t in post_tag_map_db.get(row['post_id'], []):
                    tag_counter[t] += 1
            user_tag_map[uid] = [t for t, _ in tag_counter.most_common(3)]

        # 各算法推荐
        strategies = ["hybrid", "user_cf", "content_base", "item_cf"]
        results = {}

        for strat in strategies:
            rec_list = []
            for uid in user_ids:
                try:
                    if strat == "hybrid":
                        recs = eval_rec.recommend(uid, top_k=RECOMMEND_SIZE)
                    elif strat == "user_cf":
                        recs = eval_rec._recommend_user_cf(uid, top_k=RECOMMEND_SIZE)
                    elif strat == "content_base":
                        recs = eval_rec._recommend_content_based(uid, top_k=RECOMMEND_SIZE)
                    elif strat == "item_cf":
                        recs = eval_rec._recommend_item_cf(uid, top_k=RECOMMEND_SIZE)
                    else:
                        recs = []

                    rec_ids = [r["post_id"] for r in recs]
                    rec_list.append({
                        "user_id": uid,
                        "rec_ids": rec_ids,
                        "preferred_tags": user_tag_map.get(uid, []),
                    })
                except Exception:
                    rec_list.append({
                        "user_id": uid,
                        "rec_ids": [],
                        "preferred_tags": user_tag_map.get(uid, []),
                    })
            results[strat] = rec_list

        return results

    except Exception as e:
        sys_logger.warning(f"Python 算法对比失败: {e}")
        import traceback
        traceback.print_exc()
        return {}


# ═══════════════════════════════════════════════════════════════════════════
# 6. 图表生成
# ═══════════════════════════════════════════════════════════════════════════

def _setup_matplotlib():
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        from matplotlib import rcParams
        rcParams['font.sans-serif'] = ['SimHei', 'Microsoft YaHei', 'WenQuanYi Micro Hei', 'DejaVu Sans']
        rcParams['axes.unicode_minus'] = False
        return True
    except ImportError:
        sys_logger.warning("matplotlib 未安装，跳过图表生成。")
        return False


def _generate_charts(group_metrics: dict, user_metrics: list,
                     python_comparison: dict = None, post_tag_map: dict = None,
                     offline_results: dict = None):
    """生成系统级评估图表（5 张）"""
    if not _setup_matplotlib():
        return
    import matplotlib.pyplot as plt

    group_labels = {
        "A": "活跃用户\n(Group A)",
        "B": "新用户\n(Group B)",
        "C": "轻度用户\n(Group C)"
    }
    group_colors = {"A": "#2196F3", "B": "#FF9800", "C": "#4CAF50"}
    groups = ["A", "B", "C"]

    # --- 图 1: 推荐系统综合表现 ---
    _chart_comprehensive(group_metrics, group_labels, group_colors, groups)

    # --- 图 2: 冷启动效果评估 ---
    _chart_cold_start(group_metrics, group_labels, group_colors, groups)

    # --- 图 3: 推荐价值对比 — Java 系统 vs Python 算法 ---
    if python_comparison and post_tag_map:
        _chart_java_vs_python(python_comparison, user_metrics, post_tag_map,
                              group_labels, group_colors, groups)
    else:
        _chart_tag_distribution(user_metrics, post_tag_map)

# --- 图 4: 推荐质量 @K — Java 系统 vs Python 算法 ---
    if offline_results:
        _chart_precision_at_k(user_metrics, offline_results, group_labels, group_colors, groups, post_tag_map)
    else:
        _chart_precision_at_k(user_metrics, {}, group_labels, group_colors, groups, post_tag_map)

    # --- 图 5: 汇总表格 ---
    _generate_system_summary_table(group_metrics, group_labels, group_colors)


def _chart_comprehensive(group_metrics, group_labels, group_colors, groups):
    """图 1: 推荐系统综合表现（标签覆盖度 + 多样性 + 标签一致性 + 偏好匹配率）"""
    import matplotlib.pyplot as plt

    fig, axes = plt.subplots(2, 2, figsize=(16, 12))
    fig.suptitle("推荐系统综合表现", fontsize=18, fontweight='bold')

    x = np.arange(len(groups))

    metrics = [
        ("avg_tag_coverage", "标签覆盖度", "推荐覆盖的标签占总标签比例"),
        ("avg_diversity_entropy", "多样性 (Shannon 熵)", "推荐列表标签分布均匀程度"),
        ("avg_tag_consistency", "标签一致性 (Jaccard)", "推荐标签与用户偏好匹配度"),
        ("avg_preference_match_rate", "偏好匹配率", "推荐帖子含偏好标签的比例"),
    ]

    colors_list = ["#2196F3", "#4CAF50", "#FF9800", "#E91E63"]

    for idx, (metric_key, title, subtitle) in enumerate(metrics):
        ax = axes[idx // 2][idx % 2]
        vals = [group_metrics.get(g, {}).get(metric_key, 0) for g in groups]
        bars = ax.bar(x, vals, color=[group_colors[g] for g in groups], alpha=0.85, width=0.5)
        for bar, val in zip(bars, vals):
            ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 0.01,
                    f"{val:.3f}", ha='center', va='bottom', fontsize=13, fontweight='bold')
        ax.set_xlabel("用户组", fontsize=12)
        ax.set_ylabel(title, fontsize=12)
        ax.set_xticks(x)
        ax.set_xticklabels([group_labels[g] for g in groups], fontsize=10)
        ax.set_ylim(0, min(1.1, max(vals) * 1.35 + 0.1))
        ax.set_title(f"{title}\n({subtitle})", fontsize=12, fontweight='bold')
        ax.grid(axis='y', alpha=0.3)

    plt.tight_layout(rect=[0, 0, 1, 0.94])
    p = os.path.join(CHART_DIR, "comprehensive_performance.png")
    plt.savefig(p, dpi=150, bbox_inches='tight')
    plt.close()
    sys_logger.info(f"图表已保存: {p}")


def _chart_cold_start(group_metrics, group_labels, group_colors, groups):
    """图 2: 冷启动评估"""
    import matplotlib.pyplot as plt

    fig, axes = plt.subplots(1, 3, figsize=(18, 6))
    fig.suptitle("冷启动效果评估", fontsize=16, fontweight='bold')

    x = np.arange(len(groups))

    metrics = [
        ("avg_tag_coverage", "标签覆盖度"),
        ("avg_diversity_entropy", "多样性"),
        ("avg_tag_consistency", "标签一致性"),
    ]

    for ax_idx, (metric_key, title) in enumerate(metrics):
        ax = axes[ax_idx]
        vals = [group_metrics.get(g, {}).get(metric_key, 0) for g in groups]
        bars = ax.bar(x, vals, color=[group_colors[g] for g in groups], alpha=0.85, width=0.5)
        for bar, val in zip(bars, vals):
            ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 0.01,
                    f"{val:.3f}",
                    ha='center', va='bottom', fontsize=12, fontweight='bold')
        ax.set_xlabel("用户组", fontsize=12)
        ax.set_ylabel(title, fontsize=12)
        ax.set_xticks(x)
        ax.set_xticklabels([group_labels[g] for g in groups], fontsize=10)
        ax.set_title(title, fontsize=13, fontweight='bold')
        ax.grid(axis='y', alpha=0.3)

    plt.tight_layout(rect=[0, 0, 1, 0.93])
    p = os.path.join(CHART_DIR, "cold_start_comparison.png")
    plt.savefig(p, dpi=150, bbox_inches='tight')
    plt.close()
    sys_logger.info(f"图表已保存: {p}")


def _chart_java_vs_python(python_comparison, user_metrics, post_tag_map,
                          group_labels, group_colors, groups):
    """图 3: 推荐价值对比 — Java 系统 vs Python 算法"""
    import matplotlib.pyplot as plt

    # 计算 Java 系统在活跃用户上的指标
    active_users = [u for u in user_metrics if u["group"] == "A"]

    all_tags = set()
    for tags in post_tag_map.values():
        all_tags.update(tags)

    java_metrics = {"coverage": [], "diversity": [], "preference_match": []}
    for user in active_users:
        recs = user.get("recommendations", [])
        preferred = user.get("preferred_tags", [])
        if not recs:
            continue

        # Coverage
        rec_tags = set()
        for post in recs:
            pid = post.get("id") if isinstance(post, dict) else post
            rec_tags.update(post_tag_map.get(pid, []))
        java_metrics["coverage"].append(len(rec_tags) / len(all_tags) if all_tags else 0.0)

        # Diversity
        div_ent, _ = _compute_diversity(recs, post_tag_map)
        java_metrics["diversity"].append(div_ent)

        # Preference match
        pmr = _compute_preference_match_rate(recs, preferred, post_tag_map)
        java_metrics["preference_match"].append(pmr)

    java_avg = {k: np.mean(v) if v else 0.0 for k, v in java_metrics.items()}

    # 计算 Python 各算法的指标
    strategy_labels = {
        "java_system": "Java 推荐系统\n(完整流程)",
        "hybrid": "混合推荐\n(Hybrid)",
        "user_cf": "用户协同过滤\n(UserCF)",
        "item_cf": "物品协同过滤\n(ItemCF)",
        "content_base": "内容过滤\n(ContentBase)",
    }
    strategy_order = ["java_system", "hybrid", "user_cf", "item_cf", "content_base"]
    strategy_colors = {
        "java_system": "#E91E63",
        "hybrid": "#2196F3",
        "user_cf": "#4CAF50",
        "item_cf": "#FF9800",
        "content_base": "#9C27B0",
    }

    all_strategy_metrics = {"java_system": java_avg}

    for strat, rec_list in python_comparison.items():
        strat_metrics = {"coverage": [], "diversity": [], "preference_match": []}
        for item in rec_list:
            rec_ids = item.get("rec_ids", [])
            preferred = item.get("preferred_tags", [])
            if not rec_ids:
                continue

            # Coverage
            rec_tags = set()
            for pid in rec_ids:
                rec_tags.update(post_tag_map.get(pid, []))
            strat_metrics["coverage"].append(len(rec_tags) / len(all_tags) if all_tags else 0.0)

            # Diversity
            strat_metrics["diversity"].append(_diversity_from_rec_ids(rec_ids, post_tag_map))

            # Preference match
            hits = sum(1 for pid in rec_ids if any(t in preferred for t in post_tag_map.get(pid, [])))
            strat_metrics["preference_match"].append(hits / len(rec_ids) if rec_ids else 0.0)

        all_strategy_metrics[strat] = {
            k: np.mean(v) if v else 0.0 for k, v in strat_metrics.items()
        }

    # 绘制分组柱状图
    fig, axes = plt.subplots(1, 3, figsize=(22, 7))
    fig.suptitle("推荐价值对比：Java 系统 vs Python 算法（活跃用户）",
                 fontsize=16, fontweight='bold')

    metric_names = [
        ("coverage", "标签覆盖度"),
        ("diversity", "多样性 (Shannon 熵)"),
        ("preference_match", "偏好匹配率"),
    ]

    x = np.arange(len(strategy_order))

    for ax_idx, (metric_key, title) in enumerate(metric_names):
        ax = axes[ax_idx]
        vals = [all_strategy_metrics.get(s, {}).get(metric_key, 0) for s in strategy_order]
        colors = [strategy_colors[s] for s in strategy_order]
        bars = ax.bar(x, vals, width=0.55, color=colors, alpha=0.85)
        for bar, val in zip(bars, vals):
            if val > 0:
                ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 0.005,
                        f"{val:.3f}", ha='center', va='bottom', fontsize=10, fontweight='bold')
        ax.set_ylabel(title, fontsize=12)
        ax.set_xticks(x)
        ax.set_xticklabels([strategy_labels.get(s, s) for s in strategy_order],
                           fontsize=8)
        ax.set_title(title, fontsize=13, fontweight='bold')
        ax.grid(axis='y', alpha=0.3)
        ax.set_ylim(0, max(vals) * 1.25 + 0.05)

        # 标注 Java 系统的柱子
        java_bar = bars[0]
        java_bar.set_edgecolor("#C2185B")
        java_bar.set_linewidth(2)

    plt.tight_layout(rect=[0, 0, 1, 0.92])
    p = os.path.join(CHART_DIR, "java_vs_python_comparison.png")
    plt.savefig(p, dpi=150, bbox_inches='tight')
    plt.close()
    sys_logger.info(f"图表已保存: {p}")


def _chart_tag_distribution(user_metrics, post_tag_map):
    """图 3 备选: 推荐标签分布图"""
    import matplotlib.pyplot as plt

    all_tags = sorted(set(t for tags in post_tag_map.values() for t in tags))
    groups = ["A", "B", "C"]
    group_names = ["活跃用户", "新用户", "轻度用户"]

    tag_dist = {}
    for g in groups:
        tag_counter = Counter()
        for user in user_metrics:
            if user["group"] != g:
                continue
            recs = user.get("recommendations", [])
            for post in recs:
                pid = post.get("id") if isinstance(post, dict) else post
                for t in post_tag_map.get(pid, []):
                    tag_counter[t] += 1
        total = sum(tag_counter.values())
        tag_dist[g] = {t: tag_counter.get(t, 0) / total if total > 0 else 0 for t in all_tags}

    fig, ax = plt.subplots(figsize=(12, 5))
    fig.suptitle("推荐内容标签分布对比", fontsize=15, fontweight='bold')

    x = np.arange(len(all_tags))
    width = 0.25
    offsets = [-width, 0, width]

    for i, g in enumerate(groups):
        vals = [tag_dist[g].get(t, 0) for t in all_tags]
        ax.bar(x + offsets[i], vals, width, label=group_names[i],
               color=["#2196F3", "#FF9800", "#4CAF50"][i], alpha=0.85)

    ax.set_xlabel("标签", fontsize=12)
    ax.set_ylabel("占比", fontsize=12)
    ax.set_xticks(x)
    ax.set_xticklabels(all_tags, fontsize=10)
    ax.legend(fontsize=10)
    ax.grid(axis='y', alpha=0.3)
    plt.tight_layout()
    p = os.path.join(CHART_DIR, "tag_distribution.png")
    plt.savefig(p, dpi=150, bbox_inches='tight')
    plt.close()
    sys_logger.info(f"图表已保存: {p}")


def _chart_precision_at_k(user_metrics, offline_results, group_labels, group_colors, groups, post_tag_map):
    """图 4: 推荐质量 @K — Java 系统 vs Python 算法（公平对比：相同测试用户 + Ground Truth）"""
    import matplotlib.pyplot as plt

    k_values = [5, 10, 20]
    metrics = ["precision", "recall", "hit_rate"]
    metric_labels = ["Precision@K", "Recall@K", "Hit Rate@K"]

    # Java 系统指标 (Group A, Leave-One-Out, 端到端)
    java_results = {}
    g_users = [u for u in user_metrics if u.get("group") == "A"]
    for k in k_values:
        precisions, recalls, hit_flags = [], [], []
        for user in g_users:
            recs = user.get("recommendations", [])
            if not recs or not user.get("test_relevant"):
                continue
            relevant = user["test_relevant"]
            rec_ids = [p.get("id") if isinstance(p, dict) else p for p in recs]
            m = _compute_precision_recall(rec_ids, relevant, k)
            precisions.append(m["precision"])
            recalls.append(m["recall"])
            hit_flags.append(1 if set(rec_ids[:k]) & relevant else 0)
        java_results[k] = {
            "precision": np.mean(precisions) if precisions else 0,
            "recall": np.mean(recalls) if recalls else 0,
            "hit_rate": np.mean(hit_flags) if hit_flags else 0,
        }

    # Python 各算法指标（与 Java 系统使用相同测试用户和 Ground Truth）
    strategy_labels = {
        "java_system": "Java 推荐系统\n(端到端)",
        "hybrid": "混合推荐\n(Hybrid)",
        "user_cf": "用户协同过滤\n(UserCF)",
        "item_cf": "物品协同过滤\n(ItemCF)",
        "content_base": "内容过滤\n(ContentBase)",
    }
    strategy_order = ["java_system", "hybrid", "user_cf", "item_cf", "content_base"]
    strategy_colors = {
        "java_system": "#E91E63",
        "hybrid": "#2196F3",
        "user_cf": "#4CAF50",
        "item_cf": "#FF9800",
        "content_base": "#9C27B0",
    }

    fig, axes = plt.subplots(1, 3, figsize=(20, 7))
    fig.suptitle("推荐质量对比 @K: Java 系统 vs Python 算法\n（相同测试用户 · 相同 Ground Truth · 相同训练数据）",
                 fontsize=16, fontweight='bold')

    x = np.arange(len(k_values))
    width = 0.15
    offsets = np.arange(len(strategy_order)) - (len(strategy_order) - 1) / 2

    for ax_idx, (metric, label) in enumerate(zip(metrics, metric_labels)):
        ax = axes[ax_idx]
        for s_idx, strat in enumerate(strategy_order):
            if strat == "java_system":
                values = [java_results.get(k, {}).get(metric, 0) for k in k_values]
            else:
                values = [offline_results.get(strat, {}).get(k, {}).get(metric, 0) for k in k_values]
            bars = ax.bar(x + offsets[s_idx] * width, values, width,
                          label=strategy_labels.get(strat, strat).replace("\n", " "),
                          color=strategy_colors.get(strat, "#999"), alpha=0.85)
            for bar, val in zip(bars, values):
                if val > 0.005:
                    ax.text(bar.get_x() + bar.get_width() / 2,
                            bar.get_height() + 0.003,
                            f"{val:.3f}", ha='center', va='bottom', fontsize=6, rotation=0)

        ax.set_xlabel("K 值", fontsize=12)
        ax.set_ylabel(label, fontsize=12)
        ax.set_title(label, fontsize=13, fontweight='bold')
        ax.set_xticks(x)
        ax.set_xticklabels([f"K={k}" for k in k_values])
        ax.grid(axis='y', alpha=0.3)

    # 统一图例放在底部，避免遮挡数据
    handles, labels = axes[0].get_legend_handles_labels()
    fig.legend(handles, labels, loc='lower center', ncol=5, fontsize=9,
               frameon=True, framealpha=0.9, bbox_to_anchor=(0.5, -0.02))

    plt.tight_layout(rect=[0, 0.04, 1, 0.93])
    p = os.path.join(CHART_DIR, "precision_recall_@k.png")
    plt.savefig(p, dpi=150, bbox_inches='tight')
    plt.close()
    sys_logger.info(f"图表已保存: {p}")


def _generate_system_summary_table(group_metrics, group_labels, group_colors):
    """图 5: 汇总表格"""
    import matplotlib.pyplot as plt

    groups = ["A", "B", "C"]
    metrics = [
        ("avg_tag_coverage", "标签覆盖度"),
        ("avg_diversity_entropy", "多样性 (Shannon 熵)"),
        ("avg_diversity_ratio", "多样性 (标签比)"),
        ("avg_tag_consistency", "标签一致性 (Jaccard)"),
        ("avg_preference_match_rate", "偏好匹配率"),
    ]

    col_labels = [group_labels[g].replace("\n", " ") for g in groups]
    row_labels = [m[1] for m in metrics]
    table_data = []
    best_cols = []

    for metric_key, _ in metrics:
        row_vals = [group_metrics.get(g, {}).get(metric_key, 0) for g in groups]
        best_idx = int(np.argmax(row_vals))
        best_cols.append(best_idx)
        table_data.append([f"{v:.4f}" for v in row_vals])

    fig, ax = plt.subplots(figsize=(14, 4.5))
    ax.axis('off')
    fig.suptitle("系统级评估指标汇总", fontsize=14, fontweight='bold')

    table = ax.table(cellText=table_data, rowLabels=row_labels,
                     colLabels=col_labels, loc='center', cellLoc='center')
    table.auto_set_font_size(False)
    table.set_fontsize(11)
    table.scale(1.2, 1.8)

    for j in range(len(col_labels)):
        table[0, j].set_facecolor("#2196F3")
        table[0, j].set_text_props(color="white", fontweight='bold')
    for i in range(len(row_labels)):
        if best_cols[i] < len(col_labels):
            table[i + 1, best_cols[i]].set_facecolor("#C8E6C9")

    plt.tight_layout()
    p = os.path.join(CHART_DIR, "system_summary_table.png")
    plt.savefig(p, dpi=150, bbox_inches='tight')
    plt.close()
    sys_logger.info(f"图表已保存: {p}")


# ═══════════════════════════════════════════════════════════════════════════
# 7. 数据清理
# ═══════════════════════════════════════════════════════════════════════════

def _cleanup_test_data():
    """通过数据库直接删除所有 eval_sys_ 前缀的测试用户及其交互数据"""
    try:
        from sqlalchemy import create_engine, text
        DB_URL = os.getenv("DB_URL", "mysql+pymysql://root:123456@localhost:3306/goodshare")
        engine = create_engine(DB_URL, pool_pre_ping=True)

        with engine.begin() as conn:
            rows = conn.execute(text(
                "SELECT id FROM users WHERE username LIKE :prefix"
            ), {"prefix": f"{PREFIX}%"}).fetchall()
            user_ids = [r[0] for r in rows]

            if not user_ids:
                sys_logger.info("没有找到测试用户数据，无需清理。")
                return

            uid_list = ",".join(str(uid) for uid in user_ids)
            sys_logger.info(f"正在清理 {len(user_ids)} 个测试用户及其关联数据...")

            cleanup_sqls = [
                f"DELETE FROM post_views WHERE user_id IN ({uid_list})",
                f"DELETE FROM post_likes WHERE user_id IN ({uid_list})",
                f"DELETE FROM comments WHERE user_id IN ({uid_list})",
                f"DELETE FROM favorites WHERE user_id IN ({uid_list})",
                f"DELETE FROM notifications WHERE recipient_id IN ({uid_list})",
                f"DELETE FROM messages WHERE sender_id IN ({uid_list}) OR receiver_id IN ({uid_list})",
                f"DELETE FROM follows WHERE follower_id IN ({uid_list}) OR followed_id IN ({uid_list})",
                f"DELETE FROM user_tag_weights WHERE user_id IN ({uid_list})",
                f"DELETE FROM post_tags WHERE post_id IN (SELECT id FROM posts WHERE user_id IN ({uid_list}))",
                f"DELETE FROM posts WHERE user_id IN ({uid_list})",
                f"DELETE FROM users WHERE id IN ({uid_list})",
            ]
            for sql in cleanup_sqls:
                try:
                    conn.execute(text(sql))
                except Exception as e:
                    sys_logger.debug(f"  跳过清理 SQL (可能表不存在): {e}")

        sys_logger.info(f"清理完成：已删除 {len(user_ids)} 个测试用户及关联数据。")
    except Exception as e:
        sys_logger.error(f"清理测试数据失败: {e}")
        import traceback
        traceback.print_exc()


# ═══════════════════════════════════════════════════════════════════════════
# 8. 主入口
# ═══════════════════════════════════════════════════════════════════════════

def run_system_evaluation(host: str = "localhost", port: int = 8080, cleanup: bool = True):
    """系统级端到端推荐评估"""
    sys_logger.info("=" * 70)
    sys_logger.info("          系 统 级 端 到 端 推 荐 评 估")
    sys_logger.info("=" * 70)
    sys_logger.info(f"目标服务: http://{host}:{port}")
    sys_logger.info(f"开始时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")

    client = GoodshareClient(f"http://{host}:{port}")
    rng = np.random.default_rng(42)

    # --- Step 1: 健康检查 ---
    sys_logger.info("\n[Step 1] 服务健康检查...")
    if not client.health():
        sys_logger.error("服务不可用，请先启动 Java 服务和 Python 推荐微服务。")
        return None
    sys_logger.info("  ✓ 服务正常")

    # --- Step 2: 获取基础数据 ---
    sys_logger.info("\n[Step 2] 获取基础数据...")
    all_tags_data = _fetch_all_tags(client)
    all_tags_names = set()
    tag_name_to_id = {}  # 标签名 → 标签ID 映射（用于 user_tag_weights）
    for t in all_tags_data:
        if isinstance(t, dict):
            name = t.get("name", "")
            tid = t.get("id")
            all_tags_names.add(name)
            if name and tid is not None:
                tag_name_to_id[name] = int(tid)
        elif isinstance(t, str):
            all_tags_names.add(t)

    all_posts = _fetch_all_posts(client)
    post_tag_map = _build_post_tag_map(all_posts)
    tag_post_map = _build_tag_post_map(post_tag_map)

    sys_logger.info(f"  帖子总数: {len(all_posts)}, 标签总数: {len(all_tags_names)}")

    valid_tags = [tag for tag, pids in tag_post_map.items() if len(pids) >= 3]
    if not valid_tags:
        sys_logger.error("没有可用标签或标签下帖子不足，无法评估。")
        return None
    sys_logger.info(f"  有效标签数: {len(valid_tags)}")

    # --- Step 3: 注册测试用户 ---
    sys_logger.info("\n[Step 3] 注册测试用户...")
    total_users = N_ACTIVE_USERS + N_NEW_USERS + N_LIGHT_USERS
    users = []

    for i in range(total_users):
        if i < N_ACTIVE_USERS:
            group = "A"
            idx = i
        elif i < N_ACTIVE_USERS + N_NEW_USERS:
            group = "B"
            idx = i - N_ACTIVE_USERS
        else:
            group = "C"
            idx = i - N_ACTIVE_USERS - N_NEW_USERS

        username = f"{PREFIX}{group.lower()}_{idx:03d}"

        if len(valid_tags) >= 2:
            n_tags = rng.choice([1, 2])
            preferred = rng.choice(valid_tags, size=n_tags, replace=False).tolist()
        else:
            preferred = [valid_tags[0]]

        try:
            info = _register_and_login(client, username)
            info["group"] = group
            info["username"] = username
            info["preferred_tags"] = preferred
            info["viewed_posts"] = set()
            info["liked_posts"] = set()
            info["favorited_posts"] = set()
            info["recommendations"] = []
            users.append(info)
            sys_logger.info(f"  注册成功: {username} (Group {group}, 偏好: {preferred})")
        except APIError as e:
            sys_logger.warning(f"  注册失败: {username}: {e}")

        time.sleep(0.05)

    sys_logger.info(f"  成功注册: {len(users)} / {total_users}")

    # --- Step 4: 通过数据库获取 user_id ---
    sys_logger.info("\n[Step 4] 获取用户 ID...")
    try:
        from sqlalchemy import create_engine, text
        DB_URL = os.getenv("DB_URL", "mysql+pymysql://root:123456@localhost:3306/goodshare")
        db_engine = create_engine(DB_URL, pool_pre_ping=True)
        with db_engine.connect() as conn:
            for user in users:
                row = conn.execute(text(
                    "SELECT id FROM users WHERE username = :u"
                ), {"u": user["username"]}).fetchone()
                if row:
                    user["user_id"] = row[0]
                else:
                    sys_logger.warning(f"  找不到用户 ID: {user['username']}")
    except Exception as e:
        sys_logger.error(f"  数据库查询失败: {e}")
        for i, user in enumerate(users):
            user["user_id"] = i + 1

    valid_users = [u for u in users if u.get("user_id")]
    sys_logger.info(f"  有效用户: {len(valid_users)} / {len(users)}")

    # --- Step 5: 建立 Phase 1 交互画像（不含 Phase 2，确保评估公平） ---
    sys_logger.info("\n[Step 5] 建立 Phase 1 交互画像 (Phase 2 延迟到推荐后执行)...")
    active_and_light = [u for u in valid_users if u["group"] in ("A", "C")]
    active_users = [u for u in valid_users if u["group"] == "A"]
    _build_user_profiles(client, active_and_light, tag_post_map, list(all_tags_names), rng,
                         skip_phase2=True)
    sys_logger.info("  Phase 1 交互画像建立完成。")

    # --- Step 5.1: 插入标签权重数据（模拟用户在前端调整标签偏好） ---
    # Java 端的 Global Weight Adjustment 依赖 user_tag_weights 表，
    # 如果不插入数据，标签加分/减分和强惩罚逻辑完全不会执行
    sys_logger.info("\n[Step 5.1] 插入标签权重数据 (user_tag_weights)...")
    try:
        from sqlalchemy import create_engine, text
        DB_URL = os.getenv("DB_URL", "mysql+pymysql://root:123456@localhost:3306/goodshare")
        db_engine_tw = create_engine(DB_URL, pool_pre_ping=True)
        with db_engine_tw.begin() as conn:
            for user in valid_users:
                uid = user.get("user_id")
                if uid is None:
                    continue
                preferred = user.get("preferred_tags", [])
                for tag_name in tag_name_to_id:
                    tag_id = tag_name_to_id[tag_name]
                    if tag_name in preferred:
                        # 偏好标签：权重 > 1.0（增强推荐），范围 1.1~1.7
                        weight = round(rng.uniform(1.1, 1.7), 2)
                    else:
                        # 非偏好标签：权重 < 1.0（衰减），范围 0.3~0.8
                        weight = round(rng.uniform(0.3, 0.8), 2)
                    conn.execute(text("""
                        INSERT INTO user_tag_weights (user_id, tag_id, weight)
                        VALUES (:uid, :tid, :weight)
                    """), {"uid": uid, "tid": tag_id, "weight": weight})
        n_tags = len(tag_name_to_id)
        sys_logger.info(f"  标签权重插入完成：{len(valid_users)} 用户 × {n_tags} 标签")
    except Exception as e:
        sys_logger.warning(f"  标签权重插入失败: {e}")

    # --- Step 5.5: 回读 Phase 1 交互记录 ---
    sys_logger.info("\n[Step 5.5] 回读交互记录...")
    try:
        from sqlalchemy import create_engine, text
        DB_URL = os.getenv("DB_URL", "mysql+pymysql://root:123456@localhost:3306/goodshare")
        db_engine = create_engine(DB_URL, pool_pre_ping=True)
        with db_engine.connect() as conn:
            for user in valid_users:
                uid = user.get("user_id")
                if uid is None:
                    continue
                liked = conn.execute(text(
                    "SELECT post_id FROM post_likes WHERE user_id = :u"
                ), {"u": uid}).fetchall()
                user["liked_posts"] = set(r[0] for r in liked)
                favs = conn.execute(text(
                    "SELECT post_id FROM favorites WHERE user_id = :u"
                ), {"u": uid}).fetchall()
                user["favorited_posts"] = set(r[0] for r in favs)
                viewed = conn.execute(text(
                    "SELECT post_id FROM post_views WHERE user_id = :u"
                ), {"u": uid}).fetchall()
                user["viewed_posts"] = set(r[0] for r in viewed)
        sys_logger.info("  交互记录回读完成。")
    except Exception as e:
        sys_logger.warning(f"  回读交互记录失败: {e}")

    # --- Step 6: 触发 Python 推荐模型重训 ---
    sys_logger.info("\n[Step 6] 触发 Python 推荐模型重训...")
    rec_service_url = os.getenv("REC_SERVICE_URL", "http://localhost:5000")
    retrained = client.trigger_retrain(rec_service_url)
    if not retrained:
        sys_logger.warning("  重训失败，尝试等待 30 秒后继续...")
        time.sleep(30)
    else:
        # 给 Java 服务一点时间同步
        time.sleep(3)

    # --- Step 7: 获取推荐 ---
    sys_logger.info("\n[Step 7] 获取推荐结果...")
    _fetch_recommendations(client, valid_users)

    for u in valid_users:
        rec_count = len(u.get("recommendations", []))
        sys_logger.info(f"  {u['username']} (Group {u['group']}): {rec_count} 条推荐")

    # --- Step 7.5: 执行 Phase 2 交互（Ground Truth） ---
    # 在获取推荐之后才创建 Phase 2 交互，确保 Python CF 模型
    # 在训练时不知道 Phase 2 帖子，从而可以正确评估推荐准确性
    sys_logger.info("\n[Step 7.5] 执行 Phase 2 交互 (Ground Truth)...")
    _execute_phase2_interactions(client, active_users)
    sys_logger.info("  Phase 2 交互执行完成。")

    # --- Step 8: 计算评估指标 ---
    sys_logger.info("\n[Step 8] 计算评估指标...")

    user_metrics = []
    for user in valid_users:
        recs = user.get("recommendations", [])
        preferred = user.get("preferred_tags", [])
        viewed = user.get("viewed_posts", set())

        tag_cov = _compute_tag_coverage(recs, all_tags_names, post_tag_map)
        div_entropy, div_ratio = _compute_diversity(recs, post_tag_map)
        consistency = _compute_tag_consistency(recs, preferred, post_tag_map)
        pref_match = _compute_preference_match_rate(recs, preferred, post_tag_map)

        user_metrics.append({
            "username": user["username"],
            "group": user["group"],
            "user_id": user.get("user_id"),
            "preferred_tags": preferred,
            "recommendations": recs,
            "recommend_count": len(recs),
            "tag_coverage": tag_cov,
            "diversity_entropy": div_entropy,
            "diversity_ratio": div_ratio,
            "tag_consistency": consistency,
            "preference_match_rate": pref_match,
            "viewed_count": len(viewed),
            "viewed_posts": viewed,
            "liked_posts": user.get("liked_posts", set()),
            "favorited_posts": user.get("favorited_posts", set()),
            "test_relevant": user.get("test_relevant", set()),
            "phase2_liked": user.get("phase2_liked", set()),
            "phase2_favorited": user.get("phase2_favorited", set()),
        })

    # 按组汇总
    group_metrics = {}
    for g in ["A", "B", "C"]:
        g_data = [m for m in user_metrics if m["group"] == g]
        if not g_data:
            continue
        group_metrics[g] = {
            "count": len(g_data),
            "avg_tag_coverage": np.mean([m["tag_coverage"] for m in g_data]),
            "avg_diversity_entropy": np.mean([m["diversity_entropy"] for m in g_data]),
            "avg_diversity_ratio": np.mean([m["diversity_ratio"] for m in g_data]),
            "avg_tag_consistency": np.mean([m["tag_consistency"] for m in g_data]),
            "avg_preference_match_rate": np.mean([m["preference_match_rate"] for m in g_data]),
        }

    # 打印报告
    _print_system_report(group_metrics, user_metrics)

    # --- Step 8.1: 计算并打印 Precision/Recall/Hit Rate ---
    # Group A: Leave-One-Out (Phase 2 高交互帖子作为 ground truth)
    # Group B/C: 不参与 Precision/Recall 评估
    sys_logger.info("\n[Step 8.1] 推荐质量指标 (Precision/Recall/Hit Rate)...")
    sys_logger.info("  (仅 Group A 活跃用户, Leave-One-Out 评估)")
    k_values_eval = [5, 10, 20]
    g_users = [u for u in user_metrics if u.get("group") == "A"]
    sys_logger.info(f"  --- 活跃用户 ({len(g_users)} 人) ---")

    # 输出 ground truth 统计信息（验证行为多样性）
    gt_sizes = [len(u.get("test_relevant", set())) for u in g_users if u.get("test_relevant")]
    if gt_sizes:
        from collections import Counter as _Counter
        gt_dist = _Counter(gt_sizes)
        sys_logger.info(f"  Ground Truth 统计: 平均={np.mean(gt_sizes):.1f} 篇, "
                        f"中位数={np.median(gt_sizes):.0f}, "
                        f"范围=[{min(gt_sizes)}, {max(gt_sizes)}]")
        sys_logger.info(f"  Ground Truth 分布: {dict(sorted(gt_dist.items()))}")

    # 验证 Phase 2 行为多样性
    phase2_view_sizes = [len(u.get("phase2_viewed", set())) for u in g_users if u.get("phase2_viewed")]
    phase2_like_sizes = [len(u.get("phase2_liked", set())) for u in g_users if u.get("phase2_liked")]
    phase2_fav_sizes = [len(u.get("phase2_favorited", set())) for u in g_users if u.get("phase2_favorited")]
    if phase2_view_sizes:
        sys_logger.info(f"  Phase2 浏览数分布: 范围=[{min(phase2_view_sizes)}, {max(phase2_view_sizes)}], "
                        f"平均={np.mean(phase2_view_sizes):.1f}")
        sys_logger.info(f"  Phase2 点赞数分布: 范围=[{min(phase2_like_sizes)}, {max(phase2_like_sizes)}], "
                        f"平均={np.mean(phase2_like_sizes):.1f}")
        sys_logger.info(f"  Phase2 收藏数分布: 范围=[{min(phase2_fav_sizes)}, {max(phase2_fav_sizes)}], "
                        f"平均={np.mean(phase2_fav_sizes):.1f}")
        # 点赞率分布
        like_rates = [l / v for l, v in zip(phase2_like_sizes, phase2_view_sizes) if v > 0]
        if like_rates:
            sys_logger.info(f"  Phase2 点赞率分布: 范围=[{min(like_rates):.1%}, {max(like_rates):.1%}], "
                            f"平均={np.mean(like_rates):.1%}")

    for k in k_values_eval:
        precisions, recalls, hit_flags = [], [], []
        for user in g_users:
            recs = user.get("recommendations", [])
            if not recs or not user.get("test_relevant"):
                continue
            relevant = user["test_relevant"]
            rec_ids = [p.get("id") if isinstance(p, dict) else p for p in recs]
            m = _compute_precision_recall(rec_ids, relevant, k)
            precisions.append(m["precision"])
            recalls.append(m["recall"])
            hit_flags.append(1 if set(rec_ids[:k]) & relevant else 0)
        if precisions:
            sys_logger.info(f"    K={k:2d} | Precision={np.mean(precisions):.4f} | "
                            f"Recall={np.mean(recalls):.4f} | "
                            f"Hit Rate={np.mean(hit_flags):.4f}")

    # --- Step 8.5: Python 端算法对比 ---
    sys_logger.info("\n[Step 8.5] Python 端算法对比...")
    python_comparison = _get_python_recommendations_for_real_users(sample_size=50)

    # --- Step 8.6: 离线评估算法对比（公平对比：相同测试用户 + 相同 Ground Truth） ---
    sys_logger.info("\n[Step 8.6] 离线评估算法对比 (与 Group A 公平对比)...")
    offline_results = _compute_offline_eval_results(user_metrics=user_metrics)

    # --- Step 9: 生成图表 ---
    sys_logger.info("\n[Step 9] 生成评估图表...")
    try:
        _generate_charts(group_metrics, user_metrics, python_comparison, post_tag_map,
                         offline_results)
        sys_logger.info(f"所有图表已保存到: {CHART_DIR}")
    except Exception as e:
        sys_logger.error(f"图表生成失败: {e}")
        import traceback
        traceback.print_exc()

    # --- Step 10: 清理 ---
    if cleanup:
        sys_logger.info("\n[Step 10] 清理测试数据...")
        _cleanup_test_data()
    else:
        sys_logger.info("\n[Step 10] 跳过清理 (--no-cleanup)")

    sys_logger.info("\n" + "=" * 70)
    sys_logger.info("系统级评估完成。")
    sys_logger.info("=" * 70)

    return {
        "group_metrics": group_metrics,
        "user_metrics": user_metrics,
    }


def _print_system_report(group_metrics: dict, user_metrics: list):
    """打印系统级评估报告"""
    group_labels = {
        "A": "活跃用户 (Group A)",
        "B": "新用户 (Group B)",
        "C": "轻度用户 (Group C)"
    }

    sys_logger.info("")
    sys_logger.info("=" * 70)
    sys_logger.info("                  系 统 级 评 估 报 告")
    sys_logger.info("=" * 70)

    for g in ["A", "B", "C"]:
        gm = group_metrics.get(g, {})
        if not gm:
            continue
        sys_logger.info(f"\n--- {group_labels[g]} ({gm['count']} 人) ---")
        sys_logger.info(f"  标签覆盖度       : {gm['avg_tag_coverage']:.4f}")
        sys_logger.info(f"  多样性 (熵)      : {gm['avg_diversity_entropy']:.4f}")
        sys_logger.info(f"  多样性 (标签比)  : {gm['avg_diversity_ratio']:.4f}")
        sys_logger.info(f"  标签一致性       : {gm['avg_tag_consistency']:.4f}")
        sys_logger.info(f"  偏好匹配率       : {gm['avg_preference_match_rate']:.4f}")

    # 冷启动总结
    sys_logger.info("")

    sys_logger.info("")


# ═══════════════════════════════════════════════════════════════════════════
# CLI
# ═══════════════════════════════════════════════════════════════════════════

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="系统级端到端推荐评估")
    parser.add_argument("--host", default="localhost", help="服务地址 (默认 localhost)")
    parser.add_argument("--port", type=int, default=8080, help="服务端口 (默认 8080)")
    parser.add_argument("--no-cleanup", action="store_true", help="评估后不清理测试数据")
    args = parser.parse_args()

    run_system_evaluation(host=args.host, port=args.port, cleanup=not args.no_cleanup)
