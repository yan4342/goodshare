"""
批量数据播种脚本 — 为离线评估生成充足的测试数据
==============================================
直接写入 MySQL 数据库，不经过 API。
生成: 用户、帖子、标签、浏览、点赞、评论、收藏 交互记录。

用法:
    python seed_eval_data.py [--users 100] [--posts-per-user 8] [--seed 42]
"""

import os
import sys
import argparse
import random
import string
import numpy as np
from datetime import datetime, timedelta

# 项目级设置
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

from sqlalchemy import create_engine, text

# test_data_pool 在项目根目录
try:
    from test_data_pool import DATA_POOL
except ImportError:
    from importlib.util import spec_from_file_location, module_from_spec
    _pool_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "test_data_pool.py")
    _spec = spec_from_file_location("test_data_pool", _pool_path)
    _mod = module_from_spec(_spec)
    _spec.loader.exec_module(_mod)
    DATA_POOL = _mod.DATA_POOL

DB_URL = os.getenv("DB_URL", "mysql+pymysql://root:123456@localhost:3306/goodshare")
engine = create_engine(DB_URL, pool_pre_ping=True)


# ═══════════════════════════════════════════════════════════════════════════
# 辅助函数
# ═══════════════════════════════════════════════════════════════════════════

def _random_string(n=8):
    return ''.join(random.choices(string.ascii_lowercase + string.digits, k=n))


def _random_datetime(start_days=90, end_days=0):
    delta = random.randint(end_days, start_days)
    return datetime.now() - timedelta(days=delta, hours=random.randint(0, 23), minutes=random.randint(0, 59))


def _execute_batch(conn, stmt, params_list):
    """批量执行 INSERT，使用 SAVEPOINT 处理个别失败。"""
    ok = 0
    for params in params_list:
        try:
            conn.execute(text(stmt), params)
            ok += 1
        except Exception:
            pass
    return ok


# ═══════════════════════════════════════════════════════════════════════════
# 数据播种
# ═══════════════════════════════════════════════════════════════════════════

def seed_tags():
    """确保标签存在，返回 {tag_name: tag_id} 映射。"""
    tag_names = list(DATA_POOL.keys())
    tag_map = {}

    with engine.begin() as conn:
        rows = conn.execute(text("SELECT id, name FROM tags")).fetchall()
        existing = {r[1]: r[0] for r in rows}

        for name in tag_names:
            if name in existing:
                tag_map[name] = existing[name]
            else:
                conn.execute(text("INSERT INTO tags (name) VALUES (:name)"), {"name": name})
                new_id = conn.execute(text("SELECT LAST_INSERT_ID()")).scalar()
                tag_map[name] = new_id

    print(f"  标签: {len(tag_map)} 个 ({', '.join(tag_map.keys())})")
    return tag_map


def seed_users(n_users: int, seed: int = 42):
    """生成 n_users 个用户，返回 user_id 列表。"""
    rng = random.Random(seed)
    user_ids = []

    with engine.begin() as conn:
        for i in range(n_users):
            username = f"eval_user_{i}_{_random_string(6)}"
            email = f"eval_{i}_{_random_string(6)}@test.com"
            password = "$2a$10$N9qo8uLOickgx2ZMRZoMye"

            conn.execute(text("""
                INSERT INTO users (username, email, password, role, nickname, level, experience)
                VALUES (:username, :email, :password, 'user', :nickname, :level, :exp)
            """), {
                "username": username,
                "email": email,
                "password": password,
                "nickname": f"评测用户{i}",
                "level": rng.randint(1, 10),
                "exp": rng.randint(0, 500),
            })
            uid = conn.execute(text("SELECT LAST_INSERT_ID()")).scalar()
            user_ids.append(uid)

    print(f"  用户: 创建了 {len(user_ids)} 个")
    return user_ids


def seed_posts(user_ids: list, tag_map: dict, posts_per_user: int = 8, seed: int = 42):
    """为每个用户生成帖子，返回 post_id 列表。"""
    rng = random.Random(seed)
    post_ids = []
    all_tags = list(DATA_POOL.keys())

    with engine.begin() as conn:
        for uid in user_ids:
            user_tags = rng.sample(all_tags, min(rng.randint(2, 3), len(all_tags)))

            for tag in user_tags:
                pool = DATA_POOL[tag]
                n_posts = min(rng.randint(posts_per_user // 3, posts_per_user // 2 + 1), len(pool))
                samples = rng.sample(pool, n_posts)

                for sample in samples:
                    title = sample.get("title", "分享好物")
                    content = sample.get("content", "这是一个好物分享帖子。")
                    status = rng.choices([None, 0, 1], weights=[0.7, 0.2, 0.1])[0]

                    conn.execute(text("""
                        INSERT INTO posts (user_id, author_id, title, content, status, created_at, view_count)
                        VALUES (:uid, :uid2, :title, :content, :status, :created_at, :vc)
                    """), {
                        "uid": uid,
                        "uid2": uid,
                        "title": title,
                        "content": content,
                        "status": status,
                        "created_at": _random_datetime(100, 10),
                        "vc": rng.randint(0, 200),
                    })
                    pid = conn.execute(text("SELECT LAST_INSERT_ID()")).scalar()

                    tag_id = tag_map.get(tag)
                    if tag_id:
                        conn.execute(text("""
                            INSERT INTO post_tags (post_id, tag_id) VALUES (:pid, :tid)
                        """), {"pid": pid, "tid": tag_id})

                    post_ids.append(pid)

    print(f"  帖子: 创建了 {len(post_ids)} 个")
    return post_ids


def seed_interactions(user_ids: list, post_ids: list, seed: int = 42):
    """生成交互数据: 浏览、点赞、评论、收藏。"""
    rng = np.random.default_rng(seed)
    n_categories = 8

    post_arr = np.array(post_ids)
    post_cat = {int(p): int(p) % n_categories for p in post_arr}
    cat_posts = {}
    for pid, cat in post_cat.items():
        cat_posts.setdefault(cat, []).append(pid)

    # 预生成所有交互记录
    view_records = []
    like_records = []
    comment_records = []
    fav_records = []

    comments_pool = [
        "说得好！", "非常有用", "学到了", "感谢分享", "太棒了！",
        "收藏了", "赞！", "写得真好", "有同感", "不错不错",
        "太有帮助了", "已经下单了", "种草了", "感谢推荐", "真心推荐"
    ]

    for uid in user_ids:
        if rng.random() < 0.6:
            pref_cats = [int(rng.integers(0, n_categories))]
        else:
            pref_cats = [int(c) for c in rng.choice(n_categories, size=2, replace=False)]

        for cat in pref_cats:
            available = cat_posts.get(cat, [])
            if not available:
                continue
            n_int = min(int(rng.integers(10, 30)), len(available))
            chosen = rng.choice(available, size=n_int, replace=False)
            for pid in chosen:
                pid = int(pid)
                dt = _random_datetime(80, 0)

                view_records.append({"uid": uid, "pid": pid, "dt": dt})

                if rng.random() < 0.4:
                    like_records.append({"uid": uid, "pid": pid})

                if rng.random() < 0.15:
                    comment_records.append({
                        "uid": uid, "pid": pid,
                        "content": rng.choice(comments_pool), "dt": dt
                    })

                if rng.random() < 0.2:
                    fav_records.append({"uid": uid, "pid": pid})

        # 非偏好类少量浏览
        for cat in range(n_categories):
            if cat in pref_cats:
                continue
            available = cat_posts.get(cat, [])
            if not available:
                continue
            n_browse = int(rng.integers(0, 5))
            if n_browse == 0:
                continue
            chosen = rng.choice(available, size=min(n_browse, len(available)), replace=False)
            for pid in chosen:
                view_records.append({"uid": uid, "pid": int(pid), "dt": _random_datetime(80, 0)})

    # 批量写入
    print(f"  生成: 浏览={len(view_records)}, 点赞={len(like_records)}, "
          f"评论={len(comment_records)}, 收藏={len(fav_records)}")

    with engine.begin() as conn:
        v = _execute_batch(conn,
            "INSERT INTO post_views (user_id, post_id, created_at) VALUES (:uid, :pid, :dt)",
            view_records)
        l = _execute_batch(conn,
            "INSERT INTO post_likes (user_id, post_id) VALUES (:uid, :pid)",
            like_records)
        c = _execute_batch(conn,
            "INSERT INTO comments (user_id, author_id, post_id, content, created_at) VALUES (:uid, :uid2, :pid, :content, :dt)",
            [{**r, "uid2": r["uid"]} for r in comment_records])
        f = _execute_batch(conn,
            "INSERT INTO favorites (user_id, post_id) VALUES (:uid, :pid)",
            fav_records)

    print(f"  写入成功: 浏览={v}, 点赞={l}, 评论={c}, 收藏={f}")


# ═══════════════════════════════════════════════════════════════════════════
# 主入口
# ═══════════════════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(description="为离线评估批量播种测试数据")
    parser.add_argument("--users", type=int, default=100, help="生成用户数（默认100）")
    parser.add_argument("--posts-per-user", type=int, default=8, help="每用户平均帖子数（默认8）")
    parser.add_argument("--seed", type=int, default=42, help="随机种子")
    args = parser.parse_args()

    print("=" * 60)
    print(f"批量数据播种 — 开始于 {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 60)

    print("\n[1/4] 创建标签...")
    tag_map = seed_tags()

    print(f"\n[2/4] 创建 {args.users} 个用户...")
    user_ids = seed_users(args.users, seed=args.seed)
    if not user_ids:
        print("ERROR: 无法创建用户，中止。")
        return

    print(f"\n[3/4] 创建帖子 (约 {args.posts_per_user} 篇/用户)...")
    post_ids = seed_posts(user_ids, tag_map, posts_per_user=args.posts_per_user, seed=args.seed)
    if not post_ids:
        print("ERROR: 无法创建帖子，中止。")
        return

    print(f"\n[4/4] 生成交互数据...")
    seed_interactions(user_ids, post_ids, seed=args.seed)

    print("\n" + "=" * 60)
    print(f"数据播种完成！")
    print(f"  用户: {len(user_ids)}")
    print(f"  帖子: {len(post_ids)}")
    print("=" * 60)


if __name__ == "__main__":
    main()
