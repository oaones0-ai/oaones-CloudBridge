/**
 * OAones AI 连接器选择器（OaonesAiPicker）。
 *
 * 其他页面接入：
 *   <script src="/oaones/scripts/js/aiPicker.js"></script>
 *   <div id="aiPicker"></div>
 *   <script>
 *   var picker = OaonesAiPicker.create({
 *       el: "#aiPicker",
 *       onChange: function (item, instance) { }
 *   });
 *   </script>
 *
 * 也支持声明式挂载：
 *   <div data-oaones-ai-picker data-placeholder="跟随默认 AI"></div>
 *
 * 约定：
 * - 只展示服务端返回的“已启用”连接器；
 * - 第一项“跟随默认 AI”的值为空串，后端未收到连接器 ID 时自动使用默认连接器；
 * - 禁用连接器由服务端过滤，显式传入禁用 ID 时服务端会拒绝调用。
 */
(function (global, document) {
    'use strict';

    var DEFAULT_API_URL = '/oaones/ai/page/api.jsp';

    function esc(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function resolveElement(target) {
        if (!target) { return null; }
        if (typeof target === 'string') { return document.querySelector(target); }
        return target;
    }

    function apiUrlWithAction(baseUrl) {
        var base = String(baseUrl || DEFAULT_API_URL);
        return base + (base.indexOf('?') >= 0 ? '&' : '?') + 'action=ai.connector.options';
    }

    function fetchOptions(baseUrl) {
        return fetch(apiUrlWithAction(baseUrl), {
            method: 'GET',
            credentials: 'same-origin',
            headers: { 'X-Requested-With': 'XMLHttpRequest' }
        }).then(function (response) {
            return response.json().catch(function () {
                throw new Error('AI 选择器返回了无法识别的数据（HTTP ' + response.status + '）');
            }).then(function (result) {
                if (!response.ok || !result || result.code !== 0) {
                    throw new Error((result && result.msg) || ('HTTP ' + response.status));
                }
                return result.data || {};
            });
        });
    }

    function create(options) {
        options = options || {};
        var host = resolveElement(options.el);
        if (!host) {
            return null;
        }
        if (host.__oaonesAiPicker) {
            return host.__oaonesAiPicker;
        }

        var state = {
            connectors: [],
            defaultId: '',
            value: options.value == null ? '' : String(options.value),
            loading: false,
            error: ''
        };
        var onChange = typeof options.onChange === 'function' ? options.onChange : function () {};
        var onLoad = typeof options.onLoad === 'function' ? options.onLoad : function () {};
        var placeholder = options.placeholder || '跟随默认 AI';

        host.classList.add('oa-ai-picker');
        host.innerHTML = '<select class="oa-ai-picker-select" aria-label="选择 AI 连接器"></select>'
            + '<span class="oa-ai-picker-meta" aria-live="polite"></span>';
        var select = host.querySelector('select');
        var meta = host.querySelector('.oa-ai-picker-meta');
        var destroyed = false;

        function selectedItem() {
            var id = select.value == null ? '' : String(select.value);
            if (!id) {
                return { id: '', name: placeholder, defaultConnector: true };
            }
            for (var i = 0; i < state.connectors.length; i++) {
                if (String(state.connectors[i].id) === id) {
                    return state.connectors[i];
                }
            }
            return { id: '', name: placeholder, defaultConnector: true };
        }

        function render() {
            var defaultRow = null;
            var hasConfigured = false;
            state.connectors.forEach(function (row) {
                if (row.defaultConnector || String(row.id) === String(state.defaultId)) {
                    defaultRow = row;
                }
                if (row.configured) { hasConfigured = true; }
            });

            select.innerHTML = '';
            var defaultOption = document.createElement('option');
            defaultOption.value = '';
            defaultOption.textContent = defaultRow
                ? '跟随默认：' + (defaultRow.name || '未命名 AI')
                : placeholder;
            defaultOption.disabled = !defaultRow || !defaultRow.configured;
            select.appendChild(defaultOption);

            state.connectors.forEach(function (row) {
                var option = document.createElement('option');
                option.value = String(row.id);
                option.textContent = (row.name || ('AI ' + row.id))
                    + (row.configured ? '' : '（待完善）');
                option.disabled = !row.configured;
                select.appendChild(option);
            });

            if (state.value && !select.querySelector('option[value="' + cssEscape(state.value) + '"]:not([disabled])')) {
                state.value = '';
            }
            if (!state.value && !defaultRow && hasConfigured) {
                var first = select.querySelector('option:not([disabled]):not([value=""])');
                if (first) { state.value = first.value; }
            }
            select.value = state.value;
            if (select.value !== state.value) {
                select.value = '';
                state.value = '';
            }
            select.disabled = state.loading || (!defaultRow && !hasConfigured);
            meta.textContent = state.error ? state.error : '';
            meta.className = 'oa-ai-picker-meta' + (state.error ? ' error' : '');
            meta.hidden = !state.error;
        }

        function cssEscape(value) {
            if (global.CSS && typeof global.CSS.escape === 'function') {
                return global.CSS.escape(String(value));
            }
            return String(value).replace(/["\\]/g, '\\$&');
        }

        function emit() {
            var item = selectedItem();
            onChange(item, instance);
        }

        function setValue(value) {
            state.value = value == null ? '' : String(value);
            render();
        }

        function refresh() {
            if (destroyed) { return Promise.resolve(state); }
            state.loading = true;
            state.error = '';
            select.disabled = true;
            meta.textContent = '正在加载 AI…';
            meta.className = 'oa-ai-picker-meta';
            meta.hidden = false;
            return fetchOptions(options.apiUrl).then(function (data) {
                if (destroyed) { return state; }
                state.connectors = Array.isArray(data.connectors) ? data.connectors : [];
                state.defaultId = String(data.defaultId || '');
                state.loading = false;
                render();
                onLoad(state.connectors, instance);
                return state;
            }).catch(function (error) {
                if (destroyed) { return state; }
                state.loading = false;
                state.error = error.message || 'AI 列表加载失败';
                render();
                return state;
            });
        }

        select.addEventListener('change', function () {
            state.value = select.value == null ? '' : String(select.value);
            emit();
        });

        var instance = {
            refresh: refresh,
            getValue: function () { return state.value || ''; },
            getItem: selectedItem,
            getConnectors: function () { return state.connectors.slice(0); },
            setValue: setValue,
            destroy: function () {
                destroyed = true;
                host.innerHTML = '';
                host.classList.remove('oa-ai-picker');
                try { delete host.__oaonesAiPicker; } catch (ignore) { host.__oaonesAiPicker = null; }
            }
        };
        host.__oaonesAiPicker = instance;
        refresh();
        return instance;
    }

    function mountAll(root) {
        var scope = root || document;
        var nodes = scope.querySelectorAll('[data-oaones-ai-picker]');
        for (var i = 0; i < nodes.length; i++) {
            var node = nodes[i];
            if (node.__oaonesAiPicker) { continue; }
            create({
                el: node,
                apiUrl: node.getAttribute('data-api-url') || DEFAULT_API_URL,
                value: node.getAttribute('data-value') || '',
                placeholder: node.getAttribute('data-placeholder') || '跟随默认 AI'
            });
        }
    }

    global.OaonesAiPicker = {
        DEFAULT_API_URL: DEFAULT_API_URL,
        create: create,
        mountAll: mountAll,
        esc: esc
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { mountAll(document); });
    } else {
        mountAll(document);
    }
})(window, document);
