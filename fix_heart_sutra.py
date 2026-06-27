import re

with open('index.html', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace the broken HEART_SUTRA definition (double-quoted string with unescaped newlines/quotes)
# with a template literal (backtick)
pattern = r'const HEART_SUTRA = "大正藏第 08 册.*?佛说圣佛母般若波罗蜜多经";'
replacement = """const HEART_SUTRA = `大正藏第 08 册 No. 0257 佛说圣佛母般若波罗蜜多经

No. 257 [Nos. 250-255]

佛说圣佛母般若波罗蜜多经

西天译经三藏朝奉大夫试光禄卿传法大师赐紫臣施护奉 诏译

如是我闻：

一时，世尊在王舍城鹫峰山中，与大苾刍众千二百五十人俱，并诸菩萨摩诃萨众而共围绕。

尔时，世尊即入甚深光明宣说正法三摩地。时，观自在菩萨摩诃萨在佛会中，而此菩萨摩诃萨已能修行甚深般若波罗蜜多，观见五蕴自性皆空。

尔时，尊者舍利子承佛威神，前白观自在菩萨摩萨言："若善男子、善女人，于此甚深般若波罗蜜多法门，乐欲修学者，当云何学？"

时，观自在菩萨摩诃萨告尊者舍利子言：

"汝今谛听，为汝宣说。若善男子、善女人，乐欲修学此甚深般若波罗蜜多法门者，当观五蕴自性皆空。何名五蕴自性空耶？所谓即色是空，即空是色；色无异于空，空无异于色。受、想、行、识，亦复如是。

"舍利子！此一切法如是空相，无所生无所灭，无垢染无清净，无增长无损减。舍利子！是故，空中无色，无受、想、行、识；无眼、耳、鼻、舌、身、意；无色、声、香、味、触、法；无眼界无眼识界，乃至无意界无意识界；无无明无无明尽，乃至无老死亦无老死尽；无苦、集、灭、道；无智，无所得，亦无无得。

"舍利子！由是无得故，菩萨摩诃萨依般若波罗蜜多相应行故，心无所著亦无挂碍；以无著无碍故，无有恐怖，远离一切颠倒妄想，究竟圆寂。所有三世诸佛依此般若波罗蜜多故，得阿多罗三藐三菩提。

"是故，应知般若波罗蜜多是广大明、是无上明、是无等等明，而能息除一切苦恼，是即真实无虚妄法，诸修学者当如是学。我今宣说般若波罗蜜多大明曰：

"怛[宁*也](切身)他(引)(一句) 唵(引) 誐帝(引) 帝(引引)(二) 播(引)啰帝(引)(三) 播(引)啰僧誐帝(引)(四) 冒提 莎(引)贺(引)(五)

"舍利子！诸菩萨摩诃萨，若能诵是般若波罗蜜多明句，是即修学甚深般若波罗蜜多。"

尔时，世尊从三摩地安详而起，赞观自在菩萨摩诃萨言："善哉，善哉！善男子！如汝所说，如是，如是！般若波罗蜜多当如是学，是即真实最上究竟，一切如来亦皆随喜。"

佛说此经已，观自在菩萨摩诃萨并诸苾刍，乃至世间天、人、阿修罗、乾闼婆等一切大众，闻佛所说，皆大欢喜，信受奉行。

佛说圣佛母般若波罗蜜多经`;"""

new_content, count = re.subn(pattern, replacement, content, flags=re.DOTALL)

if count > 0:
    with open('index.html', 'w', encoding='utf-8') as f:
        f.write(new_content)
    print(f'Fixed HEART_SUTRA (replaced {count} occurrence)')
else:
    print('Pattern not found, trying alternative...')
    # Try to find what's actually there
    idx = content.find('const HEART_SUTRA')
    if idx >= 0:
        print(f'Found at index {idx}')
        print(repr(content[idx:idx+100]))
    else:
        print('const HEART_SUTRA not found at all')
