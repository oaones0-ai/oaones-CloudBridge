/**
 * OAones 表单组件（OaonesForm）。
 *
 * 只处理业务无关的通用能力：读取表单值、trim、必填/长度/正则校验、字段错误状态。
 *
 * 用法：
 *   var data = OaonesForm.values(form);
 *   var result = OaonesForm.validate(form, {
 *       name: [{ type: "required", message: "请输入名称" }],
 *       email: [{ pattern: /^[^@]+@[^@]+$/, message: "邮箱格式不正确" }]
 *   });
 */
(function (global, document) {
    "use strict";

    function byId(id) {
        return id ? document.getElementById(String(id)) : null;
    }

    function trimValue(value, trim) {
        return trim !== false && typeof value === "string" ? value.replace(/^\s+|\s+$/g, "") : value;
    }

    function values(form, options) {
        options = options || {};
        var root = typeof form === "string" ? byId(form) : form;
        var out = {};
        if (!root) { return out; }
        var fields = root.querySelectorAll("input[name], select[name], textarea[name]");
        Array.prototype.forEach.call(fields, function (field) {
            if (field.disabled) { return; }
            var name = field.getAttribute("name");
            if (!name) { return; }
            var type = String(field.type || "").toLowerCase();
            if (type === "checkbox") {
                var checkboxValue = field.value === undefined || field.value === "" ? "1" : field.value;
                if (field.checked) {
                    if (options.arrayValues) {
                        if (!out[name]) { out[name] = []; }
                        if (field.value !== "on") { out[name].push(checkboxValue); }
                    } else {
                        out[name] = checkboxValue;
                    }
                } else if (!Object.prototype.hasOwnProperty.call(out, name)) {
                    out[name] = options.arrayValues ? [] : "";
                }
                return;
            }
            if (type === "radio") {
                if (field.checked) { out[name] = trimValue(field.value, options.trim); }
                return;
            }
            if (field.multiple && options.arrayValues) {
                out[name] = Array.prototype.filter.call(field.options || [], function (option) {
                    return option.selected;
                }).map(function (option) { return trimValue(option.value, options.trim); });
                return;
            }
            out[name] = trimValue(field.value, options.trim);
        });
        return out;
    }

    function field(form, name) {
        var root = typeof form === "string" ? byId(form) : form;
        return root && root.querySelector ? root.querySelector("[name='" + String(name).replace(/'/g, "\\'") + "']") : null;
    }

    function messageTarget(form, name) {
        var root = typeof form === "string" ? byId(form) : form;
        if (!root) { return null; }
        return root.querySelector("[data-oa-form-error='" + String(name).replace(/'/g, "\\'") + "']");
    }

    function setError(form, name, message) {
        var input = field(form, name);
        var target = messageTarget(form, name);
        if (input) {
            input.classList.add("oa-form-invalid");
            input.setAttribute("aria-invalid", "true");
        }
        if (target) {
            target.textContent = message || "";
            target.style.display = message ? "" : "none";
        }
        return false;
    }

    function clearError(form, name) {
        var input = field(form, name);
        var target = messageTarget(form, name);
        if (input) {
            input.classList.remove("oa-form-invalid");
            input.removeAttribute("aria-invalid");
        }
        if (target) {
            target.textContent = "";
            target.style.display = "none";
        }
    }

    function clearErrors(form) {
        var root = typeof form === "string" ? byId(form) : form;
        if (!root) { return; }
        Array.prototype.forEach.call(root.querySelectorAll(".oa-form-invalid"), function (el) {
            el.classList.remove("oa-form-invalid");
            el.removeAttribute("aria-invalid");
        });
        Array.prototype.forEach.call(root.querySelectorAll("[data-oa-form-error]"), function (el) {
            el.textContent = "";
            el.style.display = "none";
        });
    }

    function ruleMessage(rule, fallback) {
        return rule && rule.message ? rule.message : fallback;
    }

    function validate(form, schema) {
        var data = values(form);
        var errors = {};
        Object.keys(schema || {}).forEach(function (name) {
            var input = field(form, name);
            var rules = schema[name] || [];
            var value = Object.prototype.hasOwnProperty.call(data, name) ? data[name] : "";
            clearError(form, name);
            for (var i = 0; i < rules.length; i++) {
                var rule = rules[i] || {};
                var type = rule.type || "required";
                var empty = value === undefined || value === null || String(value).replace(/^\s+|\s+$/g, "") === "";
                var ok = true;
                if (type === "required") {
                    ok = !empty;
                } else if (empty) {
                    ok = true;
                } else if (type === "minLength") {
                    ok = String(value).length >= Number(rule.value || 0);
                } else if (type === "maxLength") {
                    ok = String(value).length <= Number(rule.value || 0);
                } else if (type === "pattern") {
                    ok = rule.value instanceof RegExp ? rule.value.test(String(value)) : true;
                } else if (type === "custom" && typeof rule.validator === "function") {
                    ok = rule.validator(value, data, input) !== false;
                }
                if (!ok) {
                    var message = ruleMessage(rule, "请检查" + name);
                    errors[name] = message;
                    setError(form, name, message);
                    break;
                }
            }
        });
        var first = Object.keys(errors)[0];
        if (first) {
            var firstInput = field(form, first);
            if (firstInput && firstInput.focus) { firstInput.focus(); }
            return false;
        }
        return true;
    }

    global.OaonesForm = {
        values: values,
        field: field,
        validate: validate,
        setError: setError,
        clearError: clearError,
        clearErrors: clearErrors
    };
})(window, document);
