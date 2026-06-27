var LunarCalendar = (function() {
    var lunarInfo = [
        0x04bd8,0x04ae0,0x0a570,0x054d5,0x0d260,0x0d950,0x16554,0x056a0,0x09ad0,0x055d2,
        0x04ae0,0x0a5b6,0x0a4d0,0x0d250,0x1d255,0x0b540,0x0d6a0,0x0ada2,0x095b0,0x14977,
        0x04970,0x0a4b0,0x0b4b5,0x06a50,0x06d40,0x1ab54,0x02b60,0x09570,0x052f2,0x04970,
        0x06566,0x0d4a0,0x0ea50,0x06e95,0x05ad0,0x02b60,0x186e3,0x092e0,0x1c8d7,0x0c950,
        0x0d4a0,0x1d8a6,0x0b550,0x056a0,0x1a5b4,0x025d0,0x092d0,0x0d2b2,0x0a950,0x0b557,
        0x06ca0,0x0b550,0x15355,0x04da0,0x0a5d0,0x14573,0x052d0,0x0a9a8,0x0e950,0x06aa0,
        0x0aea6,0x0ab50,0x04b60,0x0aae4,0x0a570,0x05260,0x0f263,0x0d950,0x05b57,0x056a0,
        0x096d0,0x04dd5,0x04ad0,0x0a4d0,0x0d4d4,0x0d250,0x0d558,0x0b540,0x0b5a0,0x195a6,
        0x095b0,0x049b0,0x0a974,0x0a4b0,0x0b27a,0x06a50,0x06d40,0x0af46,0x0ab60,0x09570,
        0x04af5,0x04970,0x064b0,0x074a3,0x0ea50,0x06b58,0x055c0,0x0ab60,0x096d5,0x092e0,
        0x0c960,0x0d954,0x0d4a0,0x0da50,0x07552,0x056a0,0x0abb7,0x025d0,0x092d0,0x0cab5,
        0x0a950,0x0b4a0,0x0baa4,0x0ad50,0x055d9,0x04ba0,0x0a5b0,0x15176,0x052b0,0x0a930,
        0x07954,0x06aa0,0x0ad50,0x05b52,0x04b60,0x0a6e6,0x0a4e0,0x0d260,0x0ea65,0x0d530,
        0x05aa0,0x076a3,0x096d0,0x04bd7,0x04ad0,0x0a4d0,0x1d0b6,0x0d250,0x0d520,0x0dd45,
        0x0b5a0,0x056d0,0x055b2,0x049b0,0x0a577,0x0a4b0,0x0aa50,0x1b255,0x06d20,0x0ada0
    ];

    var solarMonth = [31,28,31,30,31,30,31,31,30,31,30,31];
    var Gan = ["甲","乙","丙","丁","戊","己","庚","辛","壬","癸"];
    var Zhi = ["子","丑","寅","卯","辰","巳","午","未","申","酉","戌","亥"];
    var Animals = ["鼠","牛","虎","兔","龙","蛇","马","羊","猴","鸡","狗","猪"];
    var solarTerm = ["小寒","大寒","立春","雨水","惊蛰","春分","清明","谷雨","立夏","小满","芒种","夏至","小暑","大暑","立秋","处暑","白露","秋分","寒露","霜降","立冬","小雪","大雪","冬至"];
    var sTermInfo = [0,21208,42467,63836,85337,107014,128867,150921,173149,195551,218072,240693,263343,285989,308563,331033,353350,375494,397447,419210,440795,462224,483532,504758];
    
    var nStr1 = ["日","一","二","三","四","五","六","七","八","九","十"];
    var nStr2 = ["初","正","廿","三"];
    var nStr3 = ["日","一","二","三","四","五","六","七","八","九","十","十一","十二"];

    var lunarMonths = ["正月","二月","三月","四月","五月","六月","七月","八月","九月","十月","冬月","腊月"];
    var lunarDays = ["初一","初二","初三","初四","初五","初六","初七","初八","初九","初十",
        "十一","十二","十三","十四","十五","十六","十七","十八","十九","二十",
        "廿一","廿二","廿三","廿四","廿五","廿六","廿七","廿八","廿九","三十"];

    function isLeapYear(year) {
        return (year % 4 === 0 && year % 100 !== 0) || (year % 400 === 0);
    }

    function getDaysInMonth(year, month) {
        if (month === 1) {
            return isLeapYear(year) ? 29 : 28;
        }
        return solarMonth[month];
    }

    function getLunarMonthDays(year, month) {
        var bit = (lunarInfo[year - 1900] >> (15 - month)) & 1;
        return bit === 1 ? 30 : 29;
    }

    function getLeapMonth(year) {
        return (lunarInfo[year - 1900] & 0xf);
    }

    function getLeapMonthDays(year) {
        var leapMonth = getLeapMonth(year);
        if (leapMonth === 0) return 0;
        var bit = (lunarInfo[year - 1900] >> (15 - leapMonth)) & 1;
        return bit === 1 ? 30 : 29;
    }

    function getTerm(year, n) {
        var offDate = new Date((31556925974.7 * (year - 1900) + sTermInfo[n] * 60000) + Date.UTC(1900, 0, 6, 2, 5));
        return offDate.getUTCDate();
    }

    function getSolarTerm(year, month, day) {
        var term = null;
        var monthIdx = month - 1;
        var termIdx1 = monthIdx * 2;
        var termIdx2 = monthIdx * 2 + 1;
        var day1 = getTerm(year, termIdx1);
        var day2 = getTerm(year, termIdx2);
        if (day === day1) {
            term = solarTerm[termIdx1];
        } else if (day === day2) {
            term = solarTerm[termIdx2];
        }
        return term;
    }

    function solarToLunar(year, month, day) {
        var i, leap = 0, temp = 0;
        var offset = (Date.UTC(year, month - 1, day) - Date.UTC(1900, 0, 31)) / 86400000;
        
        for (i = 1900; i < 2101 && offset > 0; i++) {
            temp = lYearDays(i);
            offset -= temp;
        }
        if (offset < 0) {
            offset += temp;
            i--;
        }
        
        year = i;
        leap = getLeapMonth(i);
        var isLeap = false;
        
        for (i = 1; i < 13 && offset > 0; i++) {
            if (leap > 0 && i === (leap + 1) && isLeap === false) {
                --i;
                isLeap = true;
                temp = getLeapMonthDays(year);
            } else {
                temp = getLunarMonthDays(year, i);
            }
            
            if (isLeap === true && i === (leap + 1)) {
                isLeap = false;
            }
            offset -= temp;
        }
        
        if (offset === 0 && leap > 0 && i === leap + 1) {
            if (isLeap) {
                isLeap = false;
            } else {
                isLeap = true;
                --i;
            }
        }
        
        if (offset < 0) {
            offset += temp;
            --i;
        }
        
        var lMonth = i;
        var lDay = offset + 1;
        
        var solarTermName = getSolarTerm(year, month, day);
        
        return {
            year: year,
            month: lMonth,
            day: lDay,
            isLeap: isLeap,
            yearGanZhi: cyclical(year - 1900 + 36),
            monthGanZhi: cyclicalMonth(year, lMonth),
            dayGanZhi: cyclicalDay(year, lMonth, lDay),
            animal: Animals[(year - 1900) % 12],
            monthText: lunarMonths[lMonth - 1] + (isLeap ? '(闰)' : ''),
            dayText: solarTermName || lunarDays[lDay - 1],
            isTerm: !!solarTermName,
            fullText: lunarMonths[lMonth - 1] + (isLeap ? '(闰)' : '') + (solarTermName || lunarDays[lDay - 1])
        };
    }

    function lYearDays(year) {
        var i, sum = 348;
        for (i = 0x8000; i > 0x8; i >>= 1) {
            sum += (lunarInfo[year - 1900] & i) ? 1 : 0;
        }
        return (sum + getLeapMonthDays(year));
    }

    function cyclical(num) {
        return Gan[num % 10] + Zhi[num % 12];
    }

    function cyclicalMonth(year, month) {
        var monthCyl = (year - 1900) * 12 + month + 11;
        return cyclical(monthCyl);
    }

    function cyclicalDay(year, month, day) {
        var i;
        var offset = 0;
        for (i = 1900; i < year; i++) {
            offset += lYearDays(i);
        }
        for (i = 1; i < month; i++) {
            offset += getLunarMonthDays(year, i);
        }
        offset += (day - 1);
        return cyclical(offset + 40);
    }

    // 佛教节日（农历）
    // 参考标准佛教节日日期
    var BuddhistFestivals = {
        '1-1': '弥勒菩萨圣诞',
        '1-6': '定光佛圣诞',
        '1-8': '释迦牟尼佛成道日',
        '1-9': '华严菩萨圣诞',
        '2-8': '释迦牟尼佛出家日',
        '2-15': '释迦牟尼佛涅槃日',
        '2-19': '观音菩萨圣诞',
        '2-21': '普贤菩萨圣诞',
        '3-16': '准提菩萨圣诞',
        '4-4': '文殊菩萨圣诞',
        '4-8': '释迦牟尼佛诞辰',
        '4-15': '佛吉祥日',
        '5-13': '伽蓝菩萨圣诞',
        '6-3': '韦驮菩萨圣诞',
        '6-19': '观音菩萨成道日',
        '7-13': '大势至菩萨圣诞',
        '7-15': '盂兰盆节',
        '7-24': '龙树菩萨圣诞',
        '7-30': '地藏菩萨圣诞',
        '8-15': '月光菩萨圣诞',
        '8-22': '燃灯古佛圣诞',
        '9-9': '药师琉璃光如来圣诞',
        '9-19': '观世音菩萨出家日',
        '10-5': '达摩祖师圣诞',
        '11-17': '阿弥陀佛圣诞',
        '12-8': '释迦牟尼佛成道日',
        '12-29': '华严菩萨圣诞'
    };

    // 每月固定斋日
    var guanyinDays = [1, 15];
    var dizangDays = [1, 15, 18, 23, 24, 28, 29, 30];
    var shizhaiDays = [1, 8, 14, 15, 18, 23, 24, 28, 29, 30];

    function getBuddhistYear(year) {
        return year + 543;
    }

    function getEvents(lunarMonth, lunarDay) {
        var events = [];
        
        var key = lunarMonth + '-' + lunarDay;
        
        if (BuddhistFestivals[key]) {
            events.push({ type: 'festival', name: BuddhistFestivals[key] });
        }
        
        if (guanyinDays.indexOf(lunarDay) !== -1) {
            events.push({ type: 'guanyin', name: '观音斋' });
        }
        
        if (dizangDays.indexOf(lunarDay) !== -1) {
            events.push({ type: 'dizang', name: '地藏斋' });
        }
        
        if (shizhaiDays.indexOf(lunarDay) !== -1) {
            events.push({ type: 'shizhai', name: '十斋日' });
        }
        
        return events;
    }

    function getLunarDate(date) {
        var year = date.getFullYear();
        var month = date.getMonth() + 1;
        var day = date.getDate();
        return solarToLunar(year, month, day);
    }

    return {
        getLunarDate: getLunarDate,
        getBuddhistYear: getBuddhistYear,
        getEvents: getEvents,
        solarToLunar: solarToLunar,
        getSolarTerm: function(year, month, day) { return getSolarTerm(year, month, day); }
    };
})();