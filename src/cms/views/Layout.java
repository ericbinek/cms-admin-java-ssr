package cms.views;

import cms.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class Layout {

    public static final List<String> ENTITIES = List.of("BlogPosting", "Person", "Organization", "WebPage", "ImageObject", "VideoObject", "AudioObject", "CategoryCode", "CategoryCodeSet", "DefinedTerm", "DefinedTermSet", "Comment", "WebSite", "SiteNavigationElement");
    public static final Map<String, String> PLURALS = new LinkedHashMap<>();
    public static final Map<String, List<String>> DISPLAY_KEYS = new LinkedHashMap<>();
    static {
        PLURALS.put("BlogPosting", "blog-postings");
        PLURALS.put("Person", "persons");
        PLURALS.put("Organization", "organizations");
        PLURALS.put("WebPage", "web-pages");
        PLURALS.put("ImageObject", "image-objects");
        PLURALS.put("VideoObject", "video-objects");
        PLURALS.put("AudioObject", "audio-objects");
        PLURALS.put("CategoryCode", "category-codes");
        PLURALS.put("CategoryCodeSet", "category-code-sets");
        PLURALS.put("DefinedTerm", "defined-terms");
        PLURALS.put("DefinedTermSet", "defined-term-sets");
        PLURALS.put("Comment", "comments");
        PLURALS.put("WebSite", "web-sites");
        PLURALS.put("SiteNavigationElement", "site-navigation-elements");
        DISPLAY_KEYS.put("BlogPosting", List.of("headline", "alternativeHeadline"));
        DISPLAY_KEYS.put("Person", List.of("name", "givenName", "familyName"));
        DISPLAY_KEYS.put("Organization", List.of("name", "legalName"));
        DISPLAY_KEYS.put("WebPage", List.of("headline"));
        DISPLAY_KEYS.put("ImageObject", List.of("name", "caption", "contentUrl"));
        DISPLAY_KEYS.put("VideoObject", List.of("name", "caption", "contentUrl"));
        DISPLAY_KEYS.put("AudioObject", List.of("name", "contentUrl"));
        DISPLAY_KEYS.put("CategoryCode", List.of("name", "codeValue"));
        DISPLAY_KEYS.put("CategoryCodeSet", List.of("name"));
        DISPLAY_KEYS.put("DefinedTerm", List.of("name", "termCode"));
        DISPLAY_KEYS.put("DefinedTermSet", List.of("name"));
        DISPLAY_KEYS.put("Comment", List.of("text"));
        DISPLAY_KEYS.put("WebSite", List.of("name"));
        DISPLAY_KEYS.put("SiteNavigationElement", List.of("name"));
    }

    private static final Pattern FORM_ISO_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}$");

    private Layout() {}

    public static String pluralOf(String entity) {
        return PLURALS.getOrDefault(entity, entity.toLowerCase() + "s");
    }

    public static String escapeHtml(Object value) {
        if (value == null) return "";
        String s = value.toString();
        if (s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    // The hidden CSRF synchronizer field carried by every state-changing form.
    public static String csrfField(Object token) {
        return "<input type=\"hidden\" name=\"_csrf\" value=\"" + escapeHtml(token) + "\">";
    }

    @SuppressWarnings("unchecked")
    public static String layout(Map<String, Object> opts) {
        String title = (String) opts.getOrDefault("title", "CMS");
        String body = (String) opts.getOrDefault("body", "");
        Object currentEntity = opts.get("currentEntity");
        Object flash = opts.get("flash");
        Object user = opts.get("user");
        Object csrf = opts.get("csrf");

        String header;
        if (user instanceof Map) {
            Map<String, Object> u = (Map<String, Object>) user;
            StringBuilder nav = new StringBuilder();
            for (String e : ENTITIES) {
                String current = e.equals(currentEntity) ? " aria-current=\"page\"" : "";
                nav.append("<li><a href=\"/").append(PLURALS.get(e)).append("\"").append(current).append(">").append(escapeHtml(e)).append("</a></li>");
            }
            String logout = "<form method=\"POST\" action=\"/logout\" class=\"logout\">" + csrfField(csrf) + "<button type=\"submit\">Sign out</button></form>";
            header = "<header>\n" +
                "<nav aria-label=\"Primary\">\n" +
                "<ul>" + nav + "</ul>\n" +
                "</nav>\n" +
                "<p class=\"session\">Signed in as <strong>" + escapeHtml(u.get("username")) + "</strong> (" + escapeHtml(u.get("role")) + ") " + logout + "</p>\n" +
                "</header>";
        } else {
            header = "<header><p><strong>CMS Admin</strong></p></header>";
        }

        String flashEl = flash != null ? "<p role=\"status\">" + escapeHtml(flash) + "</p>" : "";
        return "<!DOCTYPE html>\n" +
            "<html lang=\"en\">\n" +
            "<head>\n" +
            "<meta charset=\"utf-8\">\n" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
            "<title>" + escapeHtml(title) + " — CMS Admin</title>\n" +
            "<link rel=\"stylesheet\" href=\"/style.css\">\n" +
            "</head>\n" +
            "<body>\n" +
            header + "\n" +
            "<main>\n" +
            "<h1>" + escapeHtml(title) + "</h1>\n" +
            flashEl + "\n" +
            body + "\n" +
            "</main>\n" +
            "</body>\n" +
            "</html>\n";
    }

    public static String displayName(Map<String, Object> item, String entity) {
        if (item == null) return "";
        for (String k : DISPLAY_KEYS.getOrDefault(entity, List.of("name", "headline"))) {
            Object v = item.get(k);
            if (v instanceof String && !((String) v).isEmpty()) return (String) v;
        }
        Object id = item.get("id");
        return id == null ? "" : id.toString();
    }

    public static Map<String, Object> errorPage(int status, String message, Object user) {
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("title", status == 404 ? "Not Found" : "Error");
        if (user != null) opts.put("user", user);
        opts.put("body", "<p role=\"alert\">" + escapeHtml(message) + "</p>");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", status);
        response.put("html", layout(opts));
        return response;
    }

    // Only http(s), mailto and site-relative values may become clickable links.
    // A stored "javascript:" or "data:" URL is rendered as inert escaped text, so
    // a bad value in the data store cannot turn into stored XSS when a user clicks
    // it.
    private static boolean isSafeHref(Object value) {
        if (!(value instanceof String)) return false;
        String v = ((String) value).trim().toLowerCase();
        return v.startsWith("http://") || v.startsWith("https://")
            || v.startsWith("mailto:") || v.startsWith("/");
    }

    private static String formatScalar(Object value, String use) {
        if ("URL".equals(use)) {
            if (!isSafeHref(value)) return escapeHtml(value);
            String v = escapeHtml(value);
            return "<a href=\"" + v + "\" rel=\"noopener noreferrer\">" + v + "</a>";
        }
        if ("DateTime".equals(use) || "Date".equals(use) || "Time".equals(use)) {
            String v = escapeHtml(value);
            return "<time datetime=\"" + v + "\">" + v + "</time>";
        }
        if ("Boolean".equals(use)) return Boolean.TRUE.equals(value) ? "Yes" : "No";
        return escapeHtml(value);
    }

    public static String formatValue(Object value, PropertySpec prop) {
        if (value == null || "".equals(value)) return "<em>—</em>";
        if (value instanceof List<?> list) {
            if (list.isEmpty()) return "<em>—</em>";
            StringBuilder sb = new StringBuilder("<ul>");
            for (Object v : list) sb.append("<li>").append(formatValue(v, prop)).append("</li>");
            sb.append("</ul>");
            return sb.toString();
        }
        return switch (prop) {
            case PropertySpec.Ref ref -> {
                String target = ref.targets().get(0);
                String plural = PLURALS.getOrDefault(target, target.toLowerCase() + "s");
                yield "<a href=\"/" + plural + "/" + escapeHtml(value) + "\">" + escapeHtml(target) + ": " + escapeHtml(value) + "</a>";
            }
            case PropertySpec.Embed em -> {
                if ("Language".equals(em.use()) && value instanceof Map<?, ?> m) {
                    Object code = m.get("alternateName");
                    if (code == null) code = m.get("name");
                    if (code == null) code = "";
                    yield "<span lang=\"" + escapeHtml(code) + "\">" + escapeHtml(code) + "</span>";
                }
                yield "<code>" + escapeHtml(Json.stringify(value)) + "</code>";
            }
            case PropertySpec.Enumerated e -> escapeHtml(value);
            case PropertySpec.Scalar s -> formatScalar(value, s.use());
        };
    }

    public static String renderField(PropertySpec prop, Object value, Map<String, List<Map<String, String>>> refOptions, List<String> errors) {
        if (refOptions == null) refOptions = Map.of();
        if (errors == null) errors = List.of();
        String name = prop.name();
        boolean required = prop.required();
        String fieldId = "field-" + name;
        String requiredAttr = required ? " required" : "";
        String requiredMark = required ? " <span aria-hidden=\"true\">*</span>" : "";
        String ariaInvalid = errors.isEmpty() ? "" : " aria-invalid=\"true\"";
        String labelText = escapeHtml(name) + requiredMark;
        StringBuilder help = new StringBuilder();
        if (!errors.isEmpty()) {
            help.append("<small role=\"alert\">");
            for (int i = 0; i < errors.size(); i++) {
                if (i > 0) help.append("; ");
                help.append(escapeHtml(errors.get(i)));
            }
            help.append("</small>");
        }
        String input = renderInput(prop, value, fieldId, requiredAttr, ariaInvalid, refOptions);
        return "<p>\n<label for=\"" + fieldId + "\">" + labelText + "</label><br>\n" + input + "\n" + help + "\n</p>";
    }

    private static String useOf(PropertySpec prop) {
        if (prop instanceof PropertySpec.Scalar s) return s.use();
        if (prop instanceof PropertySpec.Embed em) return em.use();
        return null;
    }

    @SuppressWarnings("unchecked")
    private static String renderInput(PropertySpec prop, Object value, String fieldId, String requiredAttr, String ariaInvalid, Map<String, List<Map<String, String>>> refOptions) {
        String name = escapeHtml(prop.name());
        boolean required = prop.required();
        boolean many = prop.cardinality() == PropertySpec.Cardinality.MANY;
        String maxLengthAttr = prop instanceof PropertySpec.Scalar sc && sc.maxLength() != null
            ? " maxlength=\"" + sc.maxLength() + "\""
            : "";

        if (prop instanceof PropertySpec.Enumerated en) {
            StringBuilder opts = new StringBuilder();
            for (String v : en.values()) {
                String sel = v.equals(value) ? " selected" : "";
                opts.append("<option value=\"").append(escapeHtml(v)).append("\"").append(sel).append(">").append(escapeHtml(v)).append("</option>");
            }
            String placeholder = required ? "" : "<option value=\"\">—</option>";
            return "<select id=\"" + fieldId + "\" name=\"" + name + "\"" + requiredAttr + ariaInvalid + ">" + placeholder + opts + "</select>";
        }
        if (prop instanceof PropertySpec.Ref) {
            List<Object> current;
            if (many) {
                current = value instanceof List ? new ArrayList<>((List<Object>) value) : (value == null ? new ArrayList<>() : new ArrayList<>(List.of(value)));
            } else {
                Object single = value instanceof List ? (((List<?>) value).isEmpty() ? null : ((List<?>) value).get(0)) : value;
                current = single == null ? new ArrayList<>() : new ArrayList<>(List.of(single));
            }
            StringBuilder opts = new StringBuilder();
            List<Map<String, String>> options = refOptions.getOrDefault(prop.name(), List.of());
            for (Map<String, String> o : options) {
                boolean sel = current.contains(o.get("value"));
                opts.append("<option value=\"").append(escapeHtml(o.get("value"))).append("\"")
                    .append(sel ? " selected" : "").append(">").append(escapeHtml(o.get("label"))).append("</option>");
            }
            String multiple = many ? " multiple" : "";
            String placeholder = !many && !required ? "<option value=\"\">—</option>" : "";
            return "<select id=\"" + fieldId + "\" name=\"" + name + "\"" + multiple + requiredAttr + ariaInvalid + ">" + placeholder + opts + "</select>";
        }
        if (prop instanceof PropertySpec.Embed em && "Language".equals(em.use())) {
            Object v;
            if (value instanceof Map<?, ?> m) {
                Object alt = m.get("alternateName");
                v = alt == null ? "" : alt;
            } else {
                v = value == null ? "" : value;
            }
            return "<input id=\"" + fieldId + "\" name=\"" + name + "\" type=\"text\" value=\"" + escapeHtml(v) + "\"" + requiredAttr + ariaInvalid + ">";
        }
        if (many) {
            String v;
            if (value instanceof List) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < ((List<?>) value).size(); i++) {
                    if (i > 0) sb.append("\n");
                    sb.append(((List<?>) value).get(i));
                }
                v = sb.toString();
            } else {
                v = value == null ? "" : value.toString();
            }
            return "<textarea id=\"" + fieldId + "\" name=\"" + name + "\" rows=\"3\"" + requiredAttr + ariaInvalid + ">" + escapeHtml(v) + "</textarea>";
        }
        String use = useOf(prop);
        if ("Text".equals(use) && prop instanceof PropertySpec.Scalar sml && sml.multiline()) {
            return "<textarea id=\"" + fieldId + "\" name=\"" + name + "\" rows=\"6\"" + maxLengthAttr + requiredAttr + ariaInvalid + ">" + escapeHtml(value) + "</textarea>";
        }
        if ("URL".equals(use)) {
            return "<input id=\"" + fieldId + "\" name=\"" + name + "\" type=\"url\" value=\"" + escapeHtml(value) + "\"" + maxLengthAttr + requiredAttr + ariaInvalid + ">";
        }
        if ("Integer".equals(use)) {
            String v = value == null ? "" : escapeHtml(value);
            return "<input id=\"" + fieldId + "\" name=\"" + name + "\" type=\"number\" step=\"1\" value=\"" + v + "\"" + requiredAttr + ariaInvalid + ">";
        }
        if ("Number".equals(use)) {
            String v = value == null ? "" : escapeHtml(value);
            return "<input id=\"" + fieldId + "\" name=\"" + name + "\" type=\"number\" step=\"any\" value=\"" + v + "\"" + requiredAttr + ariaInvalid + ">";
        }
        if ("Boolean".equals(use)) {
            boolean checked = Boolean.TRUE.equals(value) || "true".equals(value) || "on".equals(value);
            return "<input id=\"" + fieldId + "\" name=\"" + name + "\" type=\"checkbox\" value=\"true\"" + (checked ? " checked" : "") + ariaInvalid + ">";
        }
        if ("DateTime".equals(use) || "Date".equals(use) || "Time".equals(use)) {
            String v = "";
            if (value instanceof String) {
                String s = ((String) value).replaceAll("Z$", "");
                v = s.length() > 16 ? s.substring(0, 16) : s;
            }
            return "<input id=\"" + fieldId + "\" name=\"" + name + "\" type=\"datetime-local\" value=\"" + escapeHtml(v) + "\"" + requiredAttr + ariaInvalid + ">";
        }
        return "<input id=\"" + fieldId + "\" name=\"" + name + "\" type=\"text\" value=\"" + escapeHtml(value) + "\"" + maxLengthAttr + requiredAttr + ariaInvalid + ">";
    }

    private static Object coerceFormValue(Object raw, PropertySpec prop) {
        if (raw == null || "".equals(raw)) return null;
        if (prop instanceof PropertySpec.Enumerated || prop instanceof PropertySpec.Ref) return raw.toString();
        if (prop instanceof PropertySpec.Embed em && "Language".equals(em.use())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("@type", "Language");
            m.put("alternateName", raw.toString());
            return m;
        }
        String use = useOf(prop);
        if ("Integer".equals(use)) {
            try { return Long.parseLong(raw.toString()); } catch (NumberFormatException e) { return raw; }
        }
        if ("Number".equals(use)) {
            try { return Double.parseDouble(raw.toString()); } catch (NumberFormatException e) { return raw; }
        }
        if ("Boolean".equals(use)) {
            String s = raw.toString();
            return "true".equals(s) || "on".equals(s) || "1".equals(s);
        }
        if ("DateTime".equals(use) || "Date".equals(use) || "Time".equals(use)) {
            String s = raw.toString();
            if (FORM_ISO_PATTERN.matcher(s).matches()) return s + ":00Z";
            return s;
        }
        return raw.toString();
    }

    private static Map<String, Object> parseFormPairs(String raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String key = decodeFormPart(eq < 0 ? pair : pair.substring(0, eq));
            String value = eq < 0 ? "" : decodeFormPart(pair.substring(eq + 1));
            Object existing = out.get(key);
            if (existing == null) {
                out.put(key, value);
            } else if (existing instanceof List) {
                @SuppressWarnings("unchecked") List<Object> lst = (List<Object>) existing;
                lst.add(value);
            } else {
                List<Object> lst = new ArrayList<>();
                lst.add(existing);
                lst.add(value);
                out.put(key, lst);
            }
        }
        return out;
    }

    private static String decodeFormPart(String s) {
        try {
            return java.net.URLDecoder.decode(s.replace('+', ' '), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseFormBody(String raw, List<PropertySpec> properties) {
        Map<String, Object> pairs = parseFormPairs(raw);
        Map<String, Object> out = new LinkedHashMap<>();
        for (PropertySpec prop : properties) {
            String name = prop.name();
            boolean many = prop.cardinality() == PropertySpec.Cardinality.MANY;

            if (many) {
                List<Object> rawValues;
                if (prop instanceof PropertySpec.Ref) {
                    Object existing = pairs.get(name);
                    if (existing instanceof List) rawValues = new ArrayList<>((List<Object>) existing);
                    else if (existing != null) { rawValues = new ArrayList<>(); rawValues.add(existing); }
                    else rawValues = new ArrayList<>();
                    rawValues.removeIf(v -> v == null || "".equals(v));
                } else {
                    Object single = pairs.get(name);
                    String text = single instanceof List ? String.join("\n", ((List<?>) single).stream().map(Object::toString).toList()) : (single == null ? "" : single.toString());
                    rawValues = new ArrayList<>();
                    for (String line : text.split("\\r?\\n")) {
                        String t = line.trim();
                        if (!t.isEmpty()) rawValues.add(t);
                    }
                }
                List<Object> coerced = new ArrayList<>();
                for (Object v : rawValues) {
                    Object c = coerceFormValue(v, prop);
                    if (c != null) coerced.add(c);
                }
                if (!coerced.isEmpty()) out.put(name, coerced);
            } else if (prop instanceof PropertySpec.Scalar s && "Boolean".equals(s.use())) {
                out.put(name, pairs.containsKey(name));
            } else {
                Object rawValue = pairs.get(name);
                Object v = coerceFormValue(rawValue, prop);
                if (v != null) out.put(name, v);
            }
        }
        return out;
    }

    public static Map<String, Object> formValuesFromItem(Map<String, Object> item, List<PropertySpec> properties) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (item == null) return out;
        for (PropertySpec p : properties) {
            String name = p.name();
            if (item.containsKey(name)) out.put(name, item.get(name));
        }
        return out;
    }
}
