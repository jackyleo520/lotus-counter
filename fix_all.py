import re

with open('index.html', 'r', encoding='utf-8') as f:
    content = f.read()

# ===== 1. 修复计时器：香炉元素不存在导致JS崩溃 =====
old_incense_init = """            const incenseStick = document.getElementById('incenseStick');
            const incenseAsh = document.getElementById('incenseAsh');
            const incenseLabel = document.getElementById('incenseLabel');"""

new_incense_init = """            const incenseStick = document.getElementById('incenseStick');
            const incenseAsh = document.getElementById('incenseAsh');
            const incenseLabel = document.getElementById('incenseLabel');
            // 香炉元素可能不存在（已替换为图片），加保护
            if (!incenseStick || !incenseAsh || !incenseLabel) {
                console.warn('香炉元素不存在，计时器视觉反馈将跳过');
            }"""
content = content.replace(old_incense_init, new_incense_init)

# 修复 startCountdown
old_start = """            function startCountdown(seconds) {
                if (countdownInterval) clearInterval(countdownInterval);
                totalSessionSeconds = seconds;
                countdownSeconds = seconds;
                updateCountdownDisplay();
                updateIncense(1);
                
                // 点燃香
                incenseStick.classList.add('lit');
                
                countdownInterval = setInterval(() => {
                    countdownSeconds--;
                    updateCountdownDisplay();
                    const ratio = countdownSeconds / totalSessionSeconds;
                    updateIncense(ratio);
                    if (countdownSeconds <= 0) {
                        clearInterval(countdownInterval);
                        countdownDisplay.textContent = '00:00';
                        incenseLabel.textContent = '🕯️ 香尽';
                        // 香烧完，熄灭火焰
                        incenseStick.classList.remove('lit');
                        incenseAsh.style.height = '100%';
                    }
                }, 1000);
            }"""

new_start = """            function startCountdown(seconds) {
                if (countdownInterval) clearInterval(countdownInterval);
                totalSessionSeconds = seconds;
                countdownSeconds = seconds;
                updateCountdownDisplay();
                updateIncense(1);
                if (incenseStick) incenseStick.classList.add('lit');
                
                countdownInterval = setInterval(() => {
                    countdownSeconds--;
                    updateCountdownDisplay();
                    const ratio = countdownSeconds / totalSessionSeconds;
                    updateIncense(ratio);
                    if (countdownSeconds <= 0) {
                        clearInterval(countdownInterval);
                        countdownDisplay.textContent = '00:00';
                        if (incenseLabel) incenseLabel.textContent = '️ 香尽';
                        if (incenseStick) incenseStick.classList.remove('lit');
                        if (incenseAsh) incenseAsh.style.height = '100%';
                    }
                }, 1000);
            }"""
content = content.replace(old_start, new_start)

# 修复 updateIncense
old_update = """            function updateIncense(ratio) {
                const percent = Math.max(0, ratio * 100);
                incenseAsh.style.height = (100 - percent) + '%';
                incenseLabel.textContent = '🕯️ ' + Math.round(percent) + '%';
                if (percent < 5) {
                    incenseLabel.textContent = '🕯️ 将尽';
                }
            }"""

new_update = """            function updateIncense(ratio) {
                const percent = Math.max(0, ratio * 100);
                if (incenseAsh) incenseAsh.style.height = (100 - percent) + '%';
                if (incenseLabel) incenseLabel.textContent = '🕯️ ' + Math.round(percent) + '%';
                if (percent < 5 && incenseLabel) {
                    incenseLabel.textContent = '🕯️ 将尽';
                }
            }"""
content = content.replace(old_update, new_update)

# 修复 resetTimerUI
old_reset = """            function resetTimerUI() {
                timerState = 'idle';
                timerType = '';
                if (countdownInterval) clearInterval(countdownInterval);
                countdownInterval = null;
                if (sessionTimer) clearTimeout(sessionTimer);
                sessionTimer = null;
                countdownDisplay.textContent = '--:--';
                incenseStick.classList.remove('lit');
                incenseAsh.style.height = '0%';
                incenseLabel.textContent = '🕯️ 待燃';
            }"""

new_reset = """            function resetTimerUI() {
                timerState = 'idle';
                timerType = '';
                if (countdownInterval) clearInterval(countdownInterval);
                countdownInterval = null;
                if (sessionTimer) clearTimeout(sessionTimer);
                sessionTimer = null;
                countdownDisplay.textContent = '--:--';
                if (incenseStick) incenseStick.classList.remove('lit');
                if (incenseAsh) incenseAsh.style.height = '0%';
                if (incenseLabel) incenseLabel.textContent = '🕯️ 待燃';
            }"""
content = content.replace(old_reset, new_reset)

# 修复暂停文字
content = content.replace("incenseLabel.textContent = '️ 已暂停';", "if (incenseLabel) incenseLabel.textContent = '️ 已暂停';")

# 修复计数时点燃香
content = content.replace(
    "if (c.count === 1 && !incenseStick.classList.contains('lit')) {\n                        incenseStick.classList.add('lit');",
    "if (c.count === 1 && incenseStick && !incenseStick.classList.contains('lit')) {\n                        incenseStick.classList.add('lit');"
)

print("1. 计时器修复完成")

# ===== 2. 修复PDF/DOC/DOCX上传 =====
old_sutra_upload = """            document.getElementById('sutraFileInput').addEventListener('change', function(e) {
                const file = e.target.files[0];
                if (!file) return;
                const reader = new FileReader();
                reader.onload = function(ev) {
                    const content = ev.target.result;
                    const name = file.name.replace(/\\.[^.]+$/, '');
                    sutraList.push({ name, content, progress: 0, page: 0 });
                    renderSutraList();
                    alert('经文已导入: ' + name);
                };
                reader.readAsText(file);
                this.value = '';
            });"""

new_sutra_upload = """            document.getElementById('sutraFileInput').addEventListener('change', function(e) {
                const file = e.target.files[0];
                if (!file) return;
                const name = file.name.replace(/\\.[^.]+$/, '');
                const ext = file.name.split('.').pop().toLowerCase();
                
                if (ext === 'txt') {
                    const reader = new FileReader();
                    reader.onload = function(ev) {
                        sutraList.push({ name, content: ev.target.result, progress: 0, page: 0 });
                        renderSutraList();
                        alert('经文已导入: ' + name);
                    };
                    reader.readAsText(file, 'utf-8');
                } else if (ext === 'pdf') {
                    alert('PDF文件暂不支持直接读取。请用PDF阅读器打开后复制文本，保存为TXT文件再上传。');
                } else if (ext === 'doc' || ext === 'docx') {
                    alert('DOC/DOCX文件暂不支持直接读取。请用Word打开后另存为TXT文件再上传。');
                } else {
                    const reader = new FileReader();
                    reader.onload = function(ev) {
                        sutraList.push({ name, content: ev.target.result, progress: 0, page: 0 });
                        renderSutraList();
                        alert('经文已导入: ' + name);
                    };
                    reader.readAsText(file, 'utf-8');
                }
                this.value = '';
            });"""
content = content.replace(old_sutra_upload, new_sutra_upload)

print("2. 文件上传修复完成")

# ===== 3. 修复自定义图片适配 =====
before_pattern = r'(\.app-container::before\s*\{[^}]*?)background-size:\s*cover;'
content = re.sub(before_pattern, r'\1background-size: contain;', content, count=1)

print("3. 图片适配修复完成")

# ===== 4. 更新心经内容 =====
heart_text = """大正藏第 08 册 No. 0257 佛说圣佛母般若波罗蜜多经

No. 257 [Nos. 250-255]

佛说圣佛母般若波罗蜜多经

西天译经三藏朝奉大夫试光禄卿传法大师赐紫臣施护奉 诏译

如是我闻：

一时，世尊在王舍城鹫峰山中，与大苾刍众千二百五十人俱，并诸菩萨摩诃萨众而共围绕。

尔时，世尊即入甚深光明宣说正法三摩地。时，观自在菩萨摩诃萨在佛会中，而此菩萨摩诃萨已能修行甚深般若波罗蜜多，观见五蕴自性皆空。

尔时，尊者舍利子承佛威神，前白观自在菩萨摩诃萨言："若善男子、善女人，于此甚深般若波罗蜜多法门，乐欲修学者，当云何学？"

时，观自在菩萨摩萨告尊者舍利子言：

"汝今谛听，为汝宣说。若善男子、善女人，乐欲修学此甚深般若波罗蜜多法门者，当观五蕴自性皆空。何名五蕴自性空耶？所谓即色是空，即空是色；色无异于空，空无异于色。受、想、行、识，亦复如是。

"舍利子！此一切法如是空相，无所生无所灭，无垢染无清净，无增长无损减。舍利子！是故，空中无色，无受、想、行、识；无眼、耳、鼻、舌、身、意；无色、声、香、味、触、法；无眼界无眼识界，乃至无意界无意识界；无无明无无明尽，乃至无老死亦无老死尽；无苦、集、灭、道；无智，无所得，亦无无得。

"舍利子！由是无得故，菩萨摩诃萨依般若波罗蜜多相应行故，心无所著亦无挂碍；以无著无碍故，无有恐怖，远离一切颠倒妄想，究竟圆寂。所有三世诸佛依此般若波罗蜜多故，得阿多罗三藐三菩提。

"是故，应知般若波罗蜜多是广大明、是无上明、是无等等明，而能息除一切苦恼，是即真实无虚妄法，诸修学者当如是学。我今宣说般若波罗蜜多大明曰：

"怛[宁*也](切身)他(引)(一句) 唵(引) 誐帝(引) 帝(引引)(二) 播(引)啰誐帝(引)(三) 播(引)啰僧誐帝(引)(四) 冒提 莎(引)贺(引)(五)

"舍利子！诸菩萨摩诃萨，若能诵是般若波罗蜜多明句，是即修学甚深般若波罗蜜多。"

尔时，世尊从三摩地安详而起，赞观自在菩萨摩诃萨言："善哉，善哉！善男子！如汝所说，如是，如是！般若波罗蜜多当如是学，是即真实最上究竟，一切如来亦皆随喜。"

佛说此经已，观自在菩萨摩诃萨并诸苾，乃至世间天、人、阿修罗、乾闼婆等一切大众，闻佛所说，皆大欢喜，信受奉行。

佛说圣佛母般若波罗蜜多经"""

old_heart_pattern = r'const HEART_SUTRA = ".*?";'
escaped_heart = heart_text.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n').replace('\r', '')
new_heart_const = 'const HEART_SUTRA = "' + escaped_heart + '";'
content = re.sub(old_heart_pattern, new_heart_const, content, flags=re.DOTALL)

print("4. 心经已更新为《佛说圣佛母般若波罗蜜多经》")

# ===== 5. 药师经改名 =====
content = content.replace('{ name: "药师经", content:', '{ name: "药师七佛本愿功德经", content:')
content = content.replace('{ name: "药师经", cat:', '{ name: "药师七佛本愿功德经", cat:')
print("5. 药师经改名完成")

with open('index.html', 'w', encoding='utf-8') as f:
    f.write(content)

print("\n所有修复完成！")
