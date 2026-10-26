package dev.vtoppp.vtop.ui.section

import org.json.JSONObject

internal object SectionBridge {

    const val INTERFACE_NAME = "VTopBridge"

    private const val BASE_URL = "https://vtop.vitbhopal.ac.in/vtop/"

    private val EXTERNAL_SCRIPT = Regex(
        """<script\b[^>]*\bsrc\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)[^>]*>[\s\S]*?</script\s*>|<script\b[^>]*\bsrc\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)[^>]*>""",
        RegexOption.IGNORE_CASE,
    )

    private val LINK_TAG = Regex(
        """<link\b[^>]*\bhref\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)[^>]*>""",
        RegexOption.IGNORE_CASE,
    )

    private val TRANSLATE_SCRIPT = Regex(
        """<script\b[^>]*>(?:(?!</?script\b)[\s\S])*?translate\.google\.com(?:(?!</?script\b)[\s\S])*?</script\s*>""",
        RegexOption.IGNORE_CASE,
    )

    private val SHIM = """
        (function () {
          'use strict';

          var NOTIFY_DELAY = 300;
          var notifyTimer = null;
          var observer = null;

          function callBridge() {
            try {
              if (window.VTopBridge && typeof window.VTopBridge.onDomChanged === 'function') {
                window.VTopBridge.onDomChanged();
              }
            } catch (error) {
            }
          }

          function notify() {
            try {
              if (notifyTimer !== null) {
                clearTimeout(notifyTimer);
              }
              notifyTimer = setTimeout(function () {
                notifyTimer = null;
                callBridge();
              }, NOTIFY_DELAY);
            } catch (error) {
            }
          }

          function runScripts(container) {
            try {
              if (!container) {
                return;
              }
              var scripts = container.getElementsByTagName('script');
              var pending = [];
              for (var i = 0; i < scripts.length; i++) {
                pending.push(scripts[i]);
              }
              for (var j = 0; j < pending.length; j++) {
                try {
                  var code = pending[j].text || pending[j].textContent || '';
                  if (code) {
                    (0, eval)(code);
                  }
                } catch (error) {
                }
              }
            } catch (error) {
            }
          }

          function insertHtml(targetId, html) {
            try {
              var target = targetId ? document.getElementById(targetId) : null;
              if (!target) {
                target = document.getElementById('page-wrapper');
              }
              if (!target) {
                return false;
              }
              target.innerHTML = html;
              runScripts(target);
              notify();
              return true;
            } catch (error) {
              return false;
            }
          }

          function encodeBody(data) {
            try {
              if (typeof data === 'string') {
                return encodeURI(data);
              }
              if (data && typeof data === 'object') {
                return new URLSearchParams(data).toString();
              }
              return encodeURI(String(data));
            } catch (error) {
              return '';
            }
          }

          window.blockGUI = function () {};
          window.unblockGUI = function () {};
          window.showMessage = function () {};
          window.showMessageAndFocusInput = function () {};
          window.showAndClearMessage = function () {};
          window.callScrollTop = function () {};
          try {
            window.open = function () {};
          } catch (error) {
          }

          function ModalStub(element) {
            this.element = element;
            this.show = function () {};
            this.hide = function () {};
            this.toggle = function () {};
            this.dispose = function () {};
          }

          ModalStub.getInstance = function () {
            return new ModalStub(null);
          };

          window.bootstrap = { Modal: ModalStub };

          function ConfirmBox() {
            this.paramsdefined = false;
            this.submitTo = null;
            this.updateResponseTo = null;
            this.onSuccess = null;
            this.onError = null;
            this.onClose = null;
            this.action = null;
            this.keepOpen = false;
          }

          ConfirmBox.prototype.withParameters = function (params) {
            params = params || {};
            this.paramsdefined = true;
            this.message = params.message || 'Are you Sure ?';
            this.title = params.title || 'Confirm';
            this.confirmBtnText = params.confirmBtnText || 'Yes';
            this.cancelBtnText = params.cancelBtnText || 'No';
            this.action = typeof params.action === 'function' ? params.action : null;
            this.submitTo = params.submitTo || null;
            this.onError = typeof params.onError === 'function' ? params.onError : null;
            this.onSuccess = typeof params.onSuccess === 'function' ? params.onSuccess : null;
            this.keepOpen = params.keepOpen || false;
            this.updateResponseTo = params.updateResponseTo || null;
            this.textMessage = params.textMessage || '';
            this.onClose = typeof params.onClose === 'function' ? params.onClose : null;
            return this;
          };

          ConfirmBox.prototype.fetchCall = function (params) {
            try {
              if (!params || !params.url) {
                return Promise.reject(new Error('Required parameters missing'));
              }
              var options = { method: 'POST', credentials: 'same-origin' };
              if (params.dataform !== undefined && params.dataform !== null) {
                var form = document.getElementById(params.dataform);
                if (form) {
                  options.body = new URLSearchParams(new FormData(form)).toString();
                  options.headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
                }
              } else if (params.data !== undefined && params.data !== null) {
                options.body = encodeBody(params.data);
                options.headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
              }
              return fetch(params.url, options);
            } catch (error) {
              return Promise.reject(error);
            }
          };

          ConfirmBox.prototype.invokeSuccess = function (response) {
            try {
              if (this.onSuccess) {
                this.onSuccess(response);
              }
            } catch (error) {
            }
            notify();
          };

          ConfirmBox.prototype.invokeError = function (error) {
            try {
              if (this.onError) {
                this.onError(error);
              }
            } catch (ignored) {
            }
            notify();
          };

          ConfirmBox.prototype.updateResponse = function (response) {
            var self = this;
            if (!response) {
              this.invokeError('No response');
              return Promise.resolve(null);
            }
            if (!response.ok) {
              return response.text().then(function (text) {
                self.invokeError(text);
                return null;
              }).catch(function (error) {
                self.invokeError(error);
                return null;
              });
            }
            var headerError = null;
            try {
              headerError = response.headers.get('error');
            } catch (error) {
            }
            if (headerError && headerError !== 'null') {
              this.invokeError(headerError);
            }
            var target = null;
            try {
              target = response.headers.get('wrapper');
            } catch (error) {
            }
            if (target === 'null' || target === null || target === '') {
              target = this.updateResponseTo;
            }
            if (!target) {
              this.invokeSuccess(response);
              return Promise.resolve(null);
            }
            return response.text().then(function (html) {
              insertHtml(target, html);
              self.invokeSuccess(response);
              return null;
            }).catch(function (error) {
              self.invokeError(error);
              return null;
            });
          };

          ConfirmBox.prototype.run = function () {
            var self = this;
            if (this.submitTo && this.submitTo.url) {
              this.fetchCall(this.submitTo).then(function (response) {
                return self.updateResponse(response);
              }).catch(function (error) {
                self.invokeError(error);
              });
            } else {
              try {
                if (this.action) {
                  this.action();
                }
              } catch (error) {
                self.invokeError(error);
              }
              if (this.action) {
                this.closeModal();
              }
            }
            return this;
          };

          ConfirmBox.prototype.call = function () {
            return this.run();
          };

          ConfirmBox.prototype.show = function () {
            return this.run();
          };

          ConfirmBox.prototype.connectServer = function () {
            return this.run();
          };

          ConfirmBox.prototype.showText = function () {
            return this;
          };

          ConfirmBox.prototype.setProgressBar = function () {
            return this;
          };

          ConfirmBox.prototype.closeModal = function () {
            try {
              if (this.onClose) {
                this.onClose();
              }
            } catch (error) {
            }
            return this;
          };

          window.ConfirmBox = ConfirmBox;

          window.ajaxCall = function (urlText, dataText, target) {
            try {
              var options = { method: 'POST', credentials: 'same-origin' };
              if (dataText !== undefined && dataText !== null) {
                options.body = typeof dataText === 'string'
                  ? dataText
                  : new URLSearchParams(dataText).toString();
                options.headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
              }
              fetch(urlText, options).then(function (response) {
                if (!response.ok) {
                  throw new Error('HTTP ' + response.status);
                }
                return response.text();
              }).then(function (html) {
                insertHtml(target || 'page-wrapper', html);
              }).catch(function () {
                notify();
              });
            } catch (error) {
              notify();
            }
          };

          try {
            if (typeof window.fetch === 'function') {
              var nativeFetch = window.fetch;
              window.fetch = function () {
                return nativeFetch.apply(this, arguments).then(function (response) {
                  notify();
                  return response;
                }, function (error) {
                  notify();
                  throw error;
                });
              };
            }
          } catch (error) {
          }

          try {
            if (window.XMLHttpRequest && window.XMLHttpRequest.prototype) {
              var nativeSend = window.XMLHttpRequest.prototype.send;
              window.XMLHttpRequest.prototype.send = function () {
                try {
                  this.addEventListener('loadend', function () {
                    notify();
                  });
                } catch (error) {
                }
                return nativeSend.apply(this, arguments);
              };
            }
          } catch (error) {
          }

          function attachObserver() {
            try {
              if (observer) {
                return;
              }
              var root = document.getElementById('page-wrapper');
              if (!root || typeof MutationObserver === 'undefined') {
                return;
              }
              observer = new MutationObserver(function () {
                notify();
              });
              observer.observe(root, { childList: true, subtree: true, characterData: true });
            } catch (error) {
            }
          }

          function onReady() {
            attachObserver();
            notify();
          }

          if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', onReady);
          } else {
            onReady();
          }

          try {
            window.addEventListener('load', onReady);
          } catch (error) {
          }
        })();
    """.trimIndent()

    fun hostPage(fragmentHtml: String): String {
        val fragment = TRANSLATE_SCRIPT.replace(
            LINK_TAG.replace(
                EXTERNAL_SCRIPT.replace(fragmentHtml, ""),
                "",
            ),
            "",
        )
        return """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<base href="$BASE_URL">
<style>
html,body{margin:0;padding:0;background:transparent}
#page-wrapper{margin:0;padding:0}
</style>
<script>
$SHIM
</script>
</head>
<body>
<div id="page-wrapper">$fragment</div>
</body>
</html>"""
    }

    fun scrapeJs(): String = """
        (function () {
          try {
            var root = document.getElementById('page-wrapper');
            if (!root) {
              return '';
            }
            var nodes = root.querySelectorAll('select,button,input[type=button],input[type=submit],[onclick]');
            var index = 0;
            for (var i = 0; i < nodes.length; i++) {
              nodes[i].setAttribute('data-vtop-act', String(index));
              index += 1;
            }
            return root.innerHTML;
          } catch (error) {
            return '';
          }
        })()
    """.trimIndent()

    fun actionJs(actionIndex: Int, value: String?): String {
        val selector = JSONObject.quote("[data-vtop-act=\"$actionIndex\"]")
        val valueJs = value?.let { JSONObject.quote(it) } ?: "null"
        return """
            (function () {
              try {
                var el = document.querySelector($selector);
                if (!el) {
                  return 'missing';
                }
                var value = $valueJs;
                if (value !== null) {
                  el.value = value;
                  if (el.options) {
                    for (var i = 0; i < el.options.length; i++) {
                      if (el.options[i].value === value) {
                        el.options[i].setAttribute('selected', 'selected');
                      } else {
                        el.options[i].removeAttribute('selected');
                      }
                    }
                  }
                  try {
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                  } catch (error) {
                    try {
                      el.dispatchEvent(new Event('change'));
                    } catch (ignored) {
                    }
                  }
                } else {
                  el.click();
                }
                return 'ok';
              } catch (error) {
                return 'missing';
              }
            })()
        """.trimIndent()
    }
}
