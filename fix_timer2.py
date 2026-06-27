import re

with open(r'd:\我的文档\念佛计数器\index.html', 'r', encoding='utf-8') as f:
    content = f.read()

# Find the startDhyanaBtn event listener and replace everything until updateDhyanaDisplay function
old_block = """            document.getElementById('startDhyanaBtn').addEventListener('click', function() {
                const hours = parseInt(document.getElementById('sessionHours').value) || 0;
                const mins = parseInt(document.getElementById('sessionMinutes').value) || 0;
                const totalSeconds = hours * 3600 + mins * 60;
                if (totalSeconds <= 0) {
                    alert('请设置有效的时间');
                    return;
                }
                if (sessionTimer) clearTimeout(sessionTimer);
                startCountdown(totalSeconds);
                sessionTimer = setTimeout(() => {
                    playYinQingSound();
                    totalDhyanaSeconds += totalSeconds;
                    localStorage.setItem('total_dhyana_seconds', totalDhyanaSeconds);
                    updateDhyanaDisplay();
                    alert(`🧘 打坐结束！\\n时长：${formatDuration(Math.floor(totalSeconds / 60))}`);
                }, totalSeconds * 1000);
            });

            document.getElementById('startChantBtn').addEventListener('click', function() {
                const hours = parseInt(document.getElementById('sessionHours').value) || 0;
                const mins = parseInt(document.getElementById('sessionMinutes').value) || 0;
                const totalSeconds = hours * 3600 + mins * 60;
                if (totalSeconds <= 0) {
                    alert('请设置有效的时间');
                    return;
                }
                if (sessionTimer) clearTimeout(sessionTimer);
                startCountdown(totalSeconds);
                sessionTimer = setTimeout(() => {
                    playYinQingSound();
                    totalChantSeconds += totalSeconds;
                    localStorage.setItem('total_chant_seconds', totalChantSeconds);
                    alert(`️ 念诵结束！\\n时长：${formatDuration(Math.floor(totalSeconds / 60))}\\n计数：${counters[activeIndex].count}`);
                }, totalSeconds * 1000);
            });

            // 点击countdown直接开始计时，不弹提示
            document.getElementById('countdownDisplay').addEventListener('click', function() {
                if (countdownInterval) {
                    clearInterval(countdownInterval);
                    countdownInterval = null;
                    if (sessionTimer) {
                        clearTimeout(sessionTimer);
                        sessionTimer = null;
                    }
                    countdownDisplay.textContent = '--:--';
                    incenseStick.classList.remove('lit');
                    incenseAsh.style.height = '0%';
                    incenseLabel.textContent = '🕯️ 待燃';
                    return;
                }
                const hours = parseInt(document.getElementById('sessionHours').value) || 0;
                const mins = parseInt(document.getElementById('sessionMinutes').value) || 0;
                const totalSeconds = hours * 3600 + mins * 60;
                if (totalSeconds <= 0) {
                    alert('请设置有效的时间');
                    return;
                }
                if (sessionTimer) clearTimeout(sessionTimer);
                startCountdown(totalSeconds);
                sessionTimer = setTimeout(() => {
                    playYinQingSound();
                    alert(`🧘 功课结束！\\n${counters[activeIndex].name} 计数: ${counters[activeIndex].count}`);
                }, totalSeconds * 1000);
            });"""

new_block = """            // 计时器状态: 'idle' | 'running' | 'paused'
            let timerState = 'idle';
            let timerType = ''; // 'dhyana' | 'chant'

            function getTimerSeconds() {
                const hours = parseInt(document.getElementById('sessionHours').value) || 0;
                const mins = parseInt(document.getElementById('sessionMinutes').value) || 0;
                return hours * 3600 + mins * 60;
            }

            function resetTimerUI() {
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
            }

            function handleTimerButton(type) {
                if (timerState === 'idle') {
                    // 第一次按：开始计时
                    const totalSeconds = getTimerSeconds();
                    if (totalSeconds <= 0) {
                        alert('请设置有效的时间');
                        return;
                    }
                    timerType = type;
                    timerState = 'running';
                    if (sessionTimer) clearTimeout(sessionTimer);
                    startCountdown(totalSeconds);
                    sessionTimer = setTimeout(() => {
                        playYinQingSound();
                        if (type === 'dhyana') {
                            totalDhyanaSeconds += totalSeconds;
                            localStorage.setItem('total_dhyana_seconds', totalDhyanaSeconds);
                            updateDhyanaDisplay();
                            alert(`🧘 打坐结束！\\n时长：${formatDuration(Math.floor(totalSeconds / 60))}`);
                        } else {
                            totalChantSeconds += totalSeconds;
                            localStorage.setItem('total_chant_seconds', totalChantSeconds);
                            alert(`🕉️ 念诵结束！\\n时长：${formatDuration(Math.floor(totalSeconds / 60))}\\n计数：${counters[activeIndex].count}`);
                        }
                        resetTimerUI();
                    }, totalSeconds * 1000);
                } else if (timerState === 'running') {
                    // 第二次按：暂停
                    timerState = 'paused';
                    clearInterval(countdownInterval);
                    countdownInterval = null;
                    if (sessionTimer) clearTimeout(sessionTimer);
                    sessionTimer = null;
                    incenseLabel.textContent = '⏸️ 已暂停';
                } else if (timerState === 'paused') {
                    // 第三次按：停止并重置
                    resetTimerUI();
                }
            }

            document.getElementById('startDhyanaBtn').addEventListener('click', function() {
                handleTimerButton('dhyana');
            });

            document.getElementById('startChantBtn').addEventListener('click', function() {
                handleTimerButton('chant');
            });

            // 点击countdown直接开始计时，不弹提示
            document.getElementById('countdownDisplay').addEventListener('click', function() {
                if (countdownInterval) {
                    resetTimerUI();
                    return;
                }
                const totalSeconds = getTimerSeconds();
                if (totalSeconds <= 0) {
                    alert('请设置有效的时间');
                    return;
                }
                timerType = 'dhyana';
                timerState = 'running';
                if (sessionTimer) clearTimeout(sessionTimer);
                startCountdown(totalSeconds);
                sessionTimer = setTimeout(() => {
                    playYinQingSound();
                    alert(`🧘 功课结束！\\n${counters[activeIndex].name} 计数: ${counters[activeIndex].count}`);
                    resetTimerUI();
                }, totalSeconds * 1000);
            });"""

if old_block in content:
    content = content.replace(old_block, new_block)
    print('Replaced successfully')
else:
    print('Pattern not found! Trying line-by-line approach...')
    # Try to find the start marker
    start_marker = "document.getElementById('startDhyanaBtn').addEventListener('click', function() {"
    end_marker = "function updateDhyanaDisplay() {"
    
    start_idx = content.find(start_marker)
    end_idx = content.find(end_marker)
    
    if start_idx != -1 and end_idx != -1:
        content = content[:start_idx] + new_block + "\n            \n            " + content[end_idx:]
        print('Replaced using line-by-line approach')
    else:
        print(f'start_idx={start_idx}, end_idx={end_idx}')

with open(r'd:\我的文档\念佛计数器\index.html', 'w', encoding='utf-8') as f:
    f.write(content)
