# -*- coding: utf-8 -*-
"""发布 v1.0.1.1：复制 APK + 创建 GitHub Release + 上传资源。"""
import json, urllib.request, urllib.error, urllib.parse, os, glob, shutil

TOKEN = "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"
REPO = "jackyleo520/lotus-counter"
API = "https://api.github.com/repos/" + REPO
TAG = "v1.0.1.1"
ROOT = r"D:\我的文档\念佛计数器 - workbuddy"
REL_DIR = os.path.join(ROOT, "android-app", "app", "build", "outputs", "apk", "release")
ASSET_PATH = os.path.join(ROOT, "lotus-counter-1.0.1.1.apk")
ASSET_NAME = "lotus-counter-1.0.1.1.apk"


def req(url, method="GET", data=None, extra=None):
    headers = {"Authorization": "token " + TOKEN, "Accept": "application/vnd.github+json"}
    if extra:
        headers.update(extra)
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=120) as resp:
            return resp.read().decode("utf-8", "replace"), resp.status
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "replace")
        raise RuntimeError("HTTP %d: %s" % (e.code, body[:600]))


# 1) 定位构建产物并复制
candidates = [os.path.join(REL_DIR, "九品莲台.apk"),
              os.path.join(REL_DIR, "app-release.apk")]
src = None
for c in candidates:
    if os.path.exists(c):
        src = c
        break
if src is None:
    apks = glob.glob(os.path.join(REL_DIR, "*.apk"))
    if not apks:
        raise SystemExit("未找到构建产物 APK，请确认 assembleRelease 成功")
    src = apks[0]

shutil.copyfile(src, ASSET_PATH)
shutil.copyfile(src, os.path.join(ROOT, "九品莲台.apk"))
print("copied:", src, "->", ASSET_NAME, "(%d bytes)" % os.path.getsize(ASSET_PATH))

# 2) 创建 release
notes = ("每日开示弹窗逻辑升级：每次启动都弹窗；若当天已弹过一次，再次打开自动播下一条并循环。"
         "手动上下翻页也会记录当前位置，下次启动从当前位置继续推进。"
         "另新增桌面图形发布小工具（lotus_popup_gui.pyw）：只需粘贴正文即可发布，支持查看全部 / 新增 / 删除开示，"
         "彻底解决原命令行快捷方式“不能发送”的问题（由用户自行发布，无需重新打包）。"
         "版本 v1.0.1.1 / versionCode 14。")
body = json.dumps({
    "tag_name": TAG,
    "name": TAG,
    "body": notes,
    "draft": False,
    "prerelease": False,
}).encode("utf-8")
data, status = req(API + "/releases", "POST", body, {"Content-Type": "application/json"})
print("create release -> HTTP", status)
rel = json.loads(data)
upload_url = rel["upload_url"].split("{")[0]

# 3) 上传 APK
with open(ASSET_PATH, "rb") as f:
    bin_data = f.read()
u, st = req(upload_url + "?name=" + urllib.parse.quote(ASSET_NAME),
            "POST", bin_data,
            {"Content-Type": "application/vnd.android.package-archive"})
print("upload asset -> HTTP", st)
print("release url: https://github.com/%s/releases/tag/%s" % (REPO, TAG))
print("DONE")
