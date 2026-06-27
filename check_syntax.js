const fs = require('fs');
const vm = require('vm');
const html = fs.readFileSync('index.html', 'utf8');
const idx = html.lastIndexOf('<script>');
const endIdx = html.lastIndexOf('</script>');
if (idx >= 0 && endIdx > idx) {
    const jsCode = html.substring(idx + '<script>'.length, endIdx);
    try {
        vm.createScript(jsCode);
        console.log('JS Syntax: OK');
    } catch(e) {
        console.log('JS Syntax Error:', e.message);
        // Try to find the line
        const lines = jsCode.split('\n');
        // Try wrapping in a function to allow IIFE
        try {
            vm.createScript('(function(){' + jsCode + '})()');
            console.log('JS Syntax (wrapped): OK');
        } catch(e2) {
            console.log('JS Syntax Error (wrapped):', e2.message);
        }
    }
}
