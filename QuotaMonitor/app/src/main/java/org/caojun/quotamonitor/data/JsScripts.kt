package org.caojun.quotamonitor.data

/**
 * 注入到 WebView 的两段脚本。
 *
 * - [HOOK_SCRIPT]：在文档创建时安装 fetch / XHR 拦截器，
 *   把疑似包含额度信息的 JSON 响应通过 JavascriptInterface 回传。
 * - [DOM_SCAN_SCRIPT]：页面加载完成后执行一次，收集进度条与关键文本行。
 *
 * 注意：Kotlin raw string 中不要出现 $ 后跟 { 的序列（JS 模板字符串），
 * 脚本统一用字符串拼接。
 */
object JsScripts {

    /** 文档创建时注入（每个 WebView 只安装一次挂钩逻辑，脚本对每次导航重复执行） */
    val HOOK_SCRIPT: String = """
(function(){
  if (window.__qmHooked) { return; }
  window.__qmHooked = true;
  var MAX_LEN = 65536;
  function send(u, b){
    try { AndroidBridge.onNetwork(u, b); } catch (e) {}
  }
  function interesting(u, b){
    try {
      var lu = String(u || '').toLowerCase();
      if (/(quota|usage|billing|subscription|coding|plan|limit|credit|balance|point|rate_limit)/.test(lu)) {
        return true;
      }
      if (b && /"(quota|limit|used|consumed|remaining|usage|balance|credits?|reset_at|expires?|period)"\s*:/.test(b)) {
        return true;
      }
    } catch (e) {}
    return false;
  }
  var origFetch = window.fetch;
  if (typeof origFetch === 'function') {
    window.fetch = function(input, init){
      var url = '';
      try {
        url = (typeof input === 'string') ? input : (input && input.url) || '';
      } catch (e) {}
      var p = origFetch.apply(this, arguments);
      p.then(function(resp){
        try {
          var clone = resp.clone();
          clone.text().then(function(t){
            if (t && t.length <= MAX_LEN && interesting(url, t)) { send(url, t); }
          }).catch(function(){});
        } catch (e) {}
      }).catch(function(){});
      return p;
    };
  }
  var origOpen = XMLHttpRequest.prototype.open;
  var origSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function(method, url){
    this.__qmUrl = url;
    return origOpen.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function(){
    var self = this;
    try {
      self.addEventListener('load', function(){
        try {
          var ct = self.getResponseHeader('content-type') || '';
          var t = self.responseText || '';
          if (ct.indexOf('json') >= 0 && t.length <= MAX_LEN && interesting(self.__qmUrl, t)) {
            send(self.__qmUrl, t);
          }
        } catch (e) {}
      });
    } catch (e) {}
    return origSend.apply(this, arguments);
  };
})();
"""

    /** 页面加载完成后扫描 DOM（evaluateJavascript 执行，返回 JSON 字符串） */
    val DOM_SCAN_SCRIPT: String = """
(function(){
  var res = { url: '', title: '', hasPassword: false, loginLike: false, bars: [], lines: [] };
  try {
    res.url = location.href || '';
    res.title = document.title || '';
    res.hasPassword = !!document.querySelector('input[type=password]');
    var bodyText = document.body ? (document.body.innerText || '') : '';
    res.loginLike = /(sign in|log in|login|登录|登陆|continue with)/i.test(bodyText.slice(0, 4000));
    var nodes = document.querySelectorAll('[role=progressbar], progress, [class*=progress], [class*=gauge]');
    for (var i = 0; i < nodes.length && res.bars.length < 30; i++) {
      var el = nodes[i];
      var now = el.getAttribute('aria-valuenow');
      if (now === null && typeof el.value === 'number') { now = String(el.value); }
      if (now === null || isNaN(parseFloat(now))) { continue; }
      var max = el.getAttribute('aria-valuemax');
      if (max === null && typeof el.max === 'number' && el.max > 0) { max = String(el.max); }
      var label = '';
      var p = el.parentElement;
      for (var d = 0; d < 4 && p; d++) {
        var tx = (p.innerText || '').split('\n')[0].trim();
        if (tx && tx.length <= 60) { label = tx; break; }
        p = p.parentElement;
      }
      res.bars.push({ label: label, now: parseFloat(now), max: max ? parseFloat(max) : null });
    }
    var text = document.body ? (document.body.innerText || '') : '';
    var raw = text.split('\n');
    var seen = {};
    for (var j = 0; j < raw.length && res.lines.length < 600; j++) {
      var s = raw[j].trim();
      if (!s || s.length > 140) { continue; }
      if (seen[s]) { continue; }
      seen[s] = true;
      res.lines.push(s);
    }
  } catch (e) {}
  try { AndroidBridge.onDomScan(JSON.stringify(res)); } catch (e) {}
  return 'ok';
})()
"""
}
