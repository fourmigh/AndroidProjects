package org.caojun.cupfox.web

import org.json.JSONObject

/**
 * 注入到网页里的 CSS / JS。
 *
 * 站点是 Maccms 风格的定制模板，选择器集中在 [CSS_RULES]，
 * 站点改版时只需要调整这里。
 */
object PageInjector {

    /** 隐藏原站顶部导航（用原生顶栏替代）、页脚、广告、PC 侧栏。 */
    private val CSS_RULES = listOf(
        ".myci-header__top,.myci-header,.myci-header__bd{display:none!important}",
        "footer,.myci-footer,[class*=myci-foot]{display:none!important}",
        "[class~=ads],[class~=ad],[class*=iyads],[id*=iyads],ins.adsbygoogle{display:none!important}",
        ".col-lg-wide-25{display:none!important}",
        "body{padding-top:0!important}",
        "html,body{background:#0e0e12!important}",
    ).joinToString("\n")

    /** 只注入样式，尽早执行以减少闪烁。 */
    val INJECT_CSS_JS: String = """
        (function () {
          if (document.getElementById('cupfox-style')) return;
          var style = document.createElement('style');
          style.id = 'cupfox-style';
          style.textContent = ${JSONObject.quote(CSS_RULES)};
          (document.head || document.documentElement).appendChild(style);
        })();
    """.trimIndent()

    /**
     * 页面精简：
     * - 隐藏公告条、下载 App 推广；
     * - 把 target=_blank 改成当前页打开，否则点击影片没反应；
     * - 持续观察 DOM，处理异步插入的广告与推广。
     */
    val INJECT_JS: String = """
        (function () {
          if (!document.getElementById('cupfox-style')) {
            var style = document.createElement('style');
            style.id = 'cupfox-style';
            style.textContent = ${JSONObject.quote(CSS_RULES)};
            (document.head || document.documentElement).appendChild(style);
          }
          try { window.open = function () { return null; }; } catch (e) {}
          if (window.__cupfoxObserved) return;
          window.__cupfoxObserved = true;

          var NOISE_RE = /^(公告通知|本站所有内容均聚合|请勿相信视频|下载\s*APP|APP\s*下载|下载客户端|手机客户端)/i;

          function hideNoise() {
            var paragraphs = document.querySelectorAll('p');
            for (var i = 0; i < paragraphs.length; i++) {
              var text = (paragraphs[i].textContent || '').trim();
              if (text.indexOf('公告通知') === 0) {
                var box = paragraphs[i].parentElement;
                if (box && box !== document.body && box.children.length <= 3) {
                  box.style.setProperty('display', 'none', 'important');
                } else {
                  paragraphs[i].style.setProperty('display', 'none', 'important');
                }
              }
            }

            var links = document.querySelectorAll('a, button');
            for (var j = 0; j < links.length; j++) {
              var el = links[j];
              var label = (el.textContent || '').trim();
              if (label && label.length < 40 && NOISE_RE.test(label)) {
                var parent = el.parentElement;
                if (parent && parent !== document.body &&
                    (parent.textContent || '').trim().length < 50 &&
                    parent.children.length <= 3) {
                  parent.style.setProperty('display', 'none', 'important');
                } else {
                  el.style.setProperty('display', 'none', 'important');
                }
              }
            }

            var targets = document.querySelectorAll('a[target="_blank"]');
            for (var k = 0; k < targets.length; k++) targets[k].target = '_self';

            var iframes = document.querySelectorAll('iframe[src*=iyads], iframe[src*="ad."]');
            for (var m = 0; m < iframes.length; m++) iframes[m].style.display = 'none';
          }

          hideNoise();
          var timer = 0;
          var observer = new MutationObserver(function () {
            if (timer) return;
            timer = setTimeout(function () { timer = 0; hideNoise(); }, 300);
          });
          observer.observe(document.documentElement, { childList: true, subtree: true });
        })();
    """.trimIndent()

    /**
     * 从播放页提取真实 m3u8。
     *
     * 模板把线路放在全局 `temLineList` 中，`file` 字段形如
     * `58jaHR0cHM6...`：3 位随机前缀 + Base64(URL 编码后的地址)。
     * 另外用 `temPlayRenderCode` 里的 iframe url 参数兜底。
     */
    val EXTRACT_LINES_JS: String = """
        (function () {
          try {
            function decodeAddress(raw) {
              if (!raw) return '';
              if (/^https?:\/\//i.test(raw)) return raw;
              var start = raw.indexOf('aHR0');
              var body = start >= 0 ? raw.substring(start) : raw;
              try {
                var text = atob(body);
                try { text = decodeURIComponent(text); } catch (e) {}
                return text;
              } catch (e) {
                return '';
              }
            }

            var result = [];
            var currentId = (window.Iyplayer && window.Iyplayer.id) || 0;
            var list = window.temLineList;
            if (list && list.length) {
              for (var i = 0; i < list.length; i++) {
                var item = list[i];
                var url = decodeAddress(item.file);
                if (url && /^https?:\/\//i.test(url)) {
                  result.push({
                    name: item.name || item.subTitle || ('第' + (i + 1) + '集'),
                    url: url,
                    current: item.id === currentId
                  });
                }
              }
            }

            if (!result.length) {
              var html = String(window.temPlayRenderCode || '');
              var match = html.match(/url=([^'"&\s]+)/);
              if (match) {
                var fallback = match[1];
                try { fallback = decodeURIComponent(fallback); } catch (e) {}
                if (/^https?:\/\//i.test(fallback)) {
                  result.push({ name: '默认线路', url: fallback, current: true });
                }
              }
            }

            window.AndroidBridge.onLines(JSON.stringify(result));
          } catch (e) {
            try { window.AndroidBridge.onLines('[]'); } catch (e2) {}
          }
        })();
    """.trimIndent()

    /** 读取页面标题，写入观看历史。 */
    val EXTRACT_META_JS: String = """
        (function () {
          try {
            var og = document.querySelector('meta[property="og:image"]');
            window.AndroidBridge.onPageMeta(JSON.stringify({
              title: document.title || '',
              cover: og ? (og.getAttribute('content') || '') : ''
            }));
          } catch (e) {}
        })();
    """.trimIndent()

    /**
     * 读取原站导航，用于生成底部栏目。
     * PC / H5 两套菜单都扫一遍，按路径去重，只保留站内链接。
     */
    val EXTRACT_NAV_JS: String = """
        (function () {
          try {
            var seen = {};
            var result = [];
            var links = document.querySelectorAll(
              '.myci-header__menu a, .cms-seo006-h5-header a, .cms-seo006-pc-header a, .myci-header__menu li a'
            );
            for (var i = 0; i < links.length; i++) {
              var link = links[i];
              var href = link.getAttribute('href') || '';
              var name = (link.textContent || '').trim();
              if (!href || !name) continue;
              if (href.indexOf('javascript') === 0 || href === '#' || href.indexOf('#') === 0) continue;

              var path = href;
              if (/^https?:\/\//i.test(href)) {
                try {
                  var parsed = new URL(href);
                  if (parsed.host !== location.host) continue;
                  path = parsed.pathname + parsed.search;
                } catch (e) {
                  continue;
                }
              }
              if (path.charAt(0) !== '/') continue;
              if (seen[path]) continue;
              seen[path] = 1;
              result.push({ name: name, path: path });
            }
            window.AndroidBridge.onNav(JSON.stringify(result));
          } catch (e) {
            try { window.AndroidBridge.onNav('[]'); } catch (e2) {}
          }
        })();
    """.trimIndent()
}
