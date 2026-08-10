#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
九品莲台 · 每日开示内容更新入口
================================
每天更新一条开示/公告，用户下次启动 App 即自动获取（无需重新打包 APK）。

用法：
  1) 命令行直接给内容：
     python update_popup.py --title "今日开示标题" --content "内容，多行用 \\n 换行"

  2) 内容从文件读取（推荐长文）：
     python update_popup.py --title "今日开示标题" --file message.txt

  3) 指定日期（默认=今天）：
     python update_popup.py --date 2026-08-03 --title "..." --content "..."

  4) 查看当前所有开示：
     python update_popup.py --list

  5) 不带参数 → 进入交互模式，按提示输入（内容多行，单独一行输入 END 结束）。

原理：通过 GitHub Contents API 直接读写仓库根目录的 popup_messages.json。
     若同一天已存在，则覆盖更新；否则追加。写完即生效，用户启动 App 自动拉取。
"""
import sys
import os
import json
import base64
import urllib.request
import urllib.error
import datetime

REPO = "jackyleo520/lotus-counter"
PATH = "popup_messages.json"
BRANCH = "main"
# token：优先读环境变量 LOTUS_GH_TOKEN；否则用与 App 一致的公开仓库 token
TOKEN = os.environ.get("LOTUS_GH_TOKEN") or os.environ.get("GH_TOKEN") or ""
API_BASE = f"https://api.github.com/repos/{REPO}/contents/{PATH}"


def api_request(url, method="GET", data=None):
    headers = {
        "Authorization": f"token {TOKEN}",
        "Accept": "application/vnd.github+json",
        "Content-Type": "application/json",
    }
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.read().decode("utf-8"), r.status
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "replace")
        raise RuntimeError(f"GitHub API {e.code}: {body[:400]}")


def get_current():
    try:
        body, _ = api_request(API_BASE + f"?ref={BRANCH}")
        d = json.loads(body)
        content = base64.b64decode(d["content"]).decode("utf-8")
        parsed = json.loads(content)
        if isinstance(parsed, dict):
            messages = parsed.get("messages", [])
        elif isinstance(parsed, list):
            messages = parsed
        else:
            messages = []
        return messages, d.get("sha")
    except RuntimeError as e:
        if "404" in str(e):
            return [], None
        raise


def put_messages(messages, sha):
    messages.sort(key=lambda m: m.get("date", ""), reverse=True)
    payload = {
        "message": f"chore: update daily popup {datetime.date.today()}",
        "content": base64.b64encode(
            json.dumps({"messages": messages}, ensure_ascii=False, indent=2).encode("utf-8")
        ).decode("ascii"),
        "branch": BRANCH,
    }
    if sha:
        payload["sha"] = sha
    body, status = api_request(API_BASE, method="PUT", data=json.dumps(payload).encode("utf-8"))
    return status


def upsert(date, title, content):
    try:
        messages, sha = get_current()
        found = False
        for m in messages:
            if m.get("date") == date:
                m["title"] = title
                m["content"] = content
                found = True
                break
        if not found:
            messages.append({"date": date, "title": title, "content": content})
        status = put_messages(messages, sha)
        print()
        print(f"✅ 发布成功！已更新 {date} 的开示（HTTP {status}）")
        print(f"   用户下次启动 App 即可看到；设置页「📜 每日开示」可随时回看。")
        print(f"   在线查看：https://github.com/{REPO}/blob/{BRANCH}/{PATH}")
        print()
        print("--- 当前全部开示（最新在上）---")
        for m in sorted(messages, key=lambda x: x.get("date", ""), reverse=True):
            print(f"  {m.get('date', '?')}  {m.get('title', '')}")
    except Exception as e:
        print()
        print(f"❌ 发布失败：{e}")
        print("   排查：① 确认电脑能访问 api.github.com；② 若提示超时请重试；③ 确认仓库 token 有效。")
        raise SystemExit(1)


def list_messages():
    messages, _ = get_current()
    if not messages:
        print("（暂无开示内容，运行本脚本添加第一条）")
        return
    for m in messages:
        print(f"  {m.get('date', '?')}  {m.get('title', '')}")


def delete_message(date):
    try:
        messages, sha = get_current()
        before = len(messages)
        messages = [m for m in messages if m.get("date") != date]
        if len(messages) == before:
            print(f"⚠️ 未找到日期为 {date} 的开示，无需删除。")
            return
        put_messages(messages, sha)
        print(f"🗑️ 已删除 {date} 的开示（剩余 {len(messages)} 条）。")
    except Exception as e:
        print(f"❌ 删除失败：{e}")
        raise SystemExit(1)


def getopt(args, name):
    if name in args:
        i = args.index(name)
        if i + 1 < len(args):
            return args[i + 1]
    return None


def main():
    args = sys.argv[1:]
    if "--list" in args:
        return list_messages()
    if "--delete" in args:
        d = getopt(args, "--delete") or datetime.date.today().isoformat()
        return delete_message(d)

    date = getopt(args, "--date") or datetime.date.today().isoformat()
    title = getopt(args, "--title")
    content = getopt(args, "--content")
    filep = getopt(args, "--file")
    if filep:
        with open(filep, "r", encoding="utf-8") as f:
            content = f.read()

    if not title or not content:
        print("=== 每日开示更新（交互模式）===")
        t = input("标题（回车用默认）: ").strip() or f"每日开示 · {date}"
        print("内容（可多行；单独一行输入 END 或 “结束” 结束）：")
        lines = []
        while True:
            line = input()
            s = line.strip()
            if s in ("END", "end", "结束"):
                break
            lines.append(line)
        if not lines:
            print("⚠️ 内容为空，未发布。")
            return
        upsert(date, t, "\n".join(lines))
        return

    upsert(date, title, content)


if __name__ == "__main__":
    main()
