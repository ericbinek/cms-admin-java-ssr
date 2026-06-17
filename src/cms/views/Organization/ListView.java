package cms.views.Organization;

import cms.AdminApiClient;
import cms.views.Layout;
import cms.views.PropertySpec;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ListView {

    public static final String ENTITY = "Organization";
    public static final String BASE = "/organizations";
    public static final int DEFAULT_LIMIT = 20;
    public static final List<PropertySpec> PROPERTIES = new ArrayList<>();
    public static final List<String> EXTRA_COLS = List.of("url");

    static {
        PROPERTIES.add(new PropertySpec.Scalar("name", "Text", PropertySpec.Cardinality.ONE, true));
        PROPERTIES.add(new PropertySpec.Scalar("legalName", "Text", PropertySpec.Cardinality.ONE, false));
        PROPERTIES.add(new PropertySpec.Scalar("description", "Text", PropertySpec.Cardinality.ONE, false));
        PROPERTIES.add(new PropertySpec.Scalar("url", "URL", PropertySpec.Cardinality.ONE, false));
        PROPERTIES.add(new PropertySpec.Scalar("email", "Text", PropertySpec.Cardinality.ONE, false));
        PROPERTIES.add(new PropertySpec.Scalar("telephone", "Text", PropertySpec.Cardinality.ONE, false));
        PROPERTIES.add(new PropertySpec.Ref("logo", List.of("ImageObject"), PropertySpec.Cardinality.ONE, false));
        PROPERTIES.add(new PropertySpec.Scalar("foundingDate", "Date", PropertySpec.Cardinality.ONE, false));
        PROPERTIES.add(new PropertySpec.Scalar("sameAs", "URL", PropertySpec.Cardinality.MANY, false));
        PROPERTIES.add(new PropertySpec.Ref("parentOrganization", List.of("Organization"), PropertySpec.Cardinality.ONE, false));
    }

    private ListView() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> render(Map<String, Object> opts) {
        AdminApiClient api = (AdminApiClient) opts.get("api");
        Object user = opts.get("user");
        Object csrf = opts.get("csrf");
        String url = (String) opts.getOrDefault("url", BASE);
        Map<String, Object> query = new LinkedHashMap<>();
        String rawQuery = URI.create(url).getRawQuery();
        if (rawQuery != null) {
            for (String pair : rawQuery.split("&")) {
                int eq = pair.indexOf('=');
                if (eq < 0) continue;
                String k = pair.substring(0, eq);
                String v = pair.substring(eq + 1);
                if (k.equals("limit") || k.equals("offset") || k.equals("sort") || k.equals("order")) {
                    query.put(k, java.net.URLDecoder.decode(v, java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        }
        AdminApiClient.Response r = api.list(ENTITY, query);
        if (r.status != 200) {
            String msg = "unknown error";
            if (r.body instanceof Map) {
                Object m = ((Map<?, ?>) r.body).get("message");
                if (m instanceof String) msg = (String) m;
            }
            Map<String, Object> opts2 = new LinkedHashMap<>();
            opts2.put("title", ENTITY + "s");
            opts2.put("currentEntity", ENTITY);
            if (user != null) opts2.put("user", user);
            opts2.put("csrf", csrf);
            opts2.put("body", "<p role=\"alert\">Failed to load: " + Layout.escapeHtml(msg) + "</p>");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", r.status);
            out.put("html", Layout.layout(opts2));
            return out;
        }
        Map<String, Object> body = (Map<String, Object>) r.body;
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        Number total = (Number) body.get("total");

        StringBuilder headers = new StringBuilder();
        List<String> headerList = new ArrayList<>();
        headerList.add("Name");
        headerList.add("Created");
        headerList.addAll(EXTRA_COLS);
        headerList.add("Actions");
        for (String h : headerList) headers.append("<th scope=\"col\">").append(Layout.escapeHtml(h)).append("</th>");

        Map<String, PropertySpec> propByName = new LinkedHashMap<>();
        for (PropertySpec p : PROPERTIES) propByName.put(p.name(), p);

        StringBuilder rows = new StringBuilder();
        for (Map<String, Object> item : items) {
            StringBuilder extras = new StringBuilder();
            for (String col : EXTRA_COLS) {
                PropertySpec p = propByName.get(col);
                extras.append("<td>").append(p != null ? Layout.formatValue(item.get(col), p) : Layout.escapeHtml(item.getOrDefault(col, "").toString())).append("</td>");
            }
            String id = Layout.escapeHtml(item.get("id"));
            rows.append("<tr>\n")
                .append("<td><a href=\"").append(BASE).append("/").append(id).append("\">").append(Layout.escapeHtml(Layout.displayName(item, ENTITY))).append("</a></td>\n")
                .append("<td><time datetime=\"").append(Layout.escapeHtml(item.getOrDefault("dateCreated", ""))).append("\">").append(Layout.escapeHtml(item.getOrDefault("dateCreated", ""))).append("</time></td>\n")
                .append(extras).append("\n")
                .append("<td><a href=\"").append(BASE).append("/").append(id).append("/edit\">Edit</a> · <a href=\"").append(BASE).append("/").append(id).append("/delete\">Delete</a></td>\n")
                .append("</tr>");
        }
        if (rows.length() == 0) {
            int cols = 3 + EXTRA_COLS.size();
            rows.append("<tr><td colspan=\"").append(cols).append("\"><em>No items.</em></td></tr>");
        }

        int limit = paginationValue(query.get("limit"), DEFAULT_LIMIT, 1);
        int offset = paginationValue(query.get("offset"), 0, 0);
        long count = total != null ? total.longValue() : 0;
        StringBuilder pagination = new StringBuilder();
        if (offset > 0) {
            String href = Layout.escapeHtml(pageHref(rawQuery, Math.max(0, offset - limit)));
            pagination.append("<a href=\"").append(href).append("\" rel=\"prev\">Previous</a>");
        }
        if (offset + limit < count) {
            String href = Layout.escapeHtml(pageHref(rawQuery, offset + limit));
            pagination.append("<a href=\"").append(href).append("\" rel=\"next\">Next</a>");
        }
        String paginationHtml = pagination.length() > 0
            ? "\n<nav aria-label=\"Pagination\">" + pagination + "</nav>"
            : "";

        String bodyHtml =
            "<p><a href=\"" + BASE + "/new\">New " + Layout.escapeHtml(ENTITY) + "</a></p>\n" +
            "<p>Showing " + items.size() + " of " + total + ".</p>\n" +
            "<table>\n" +
            "<caption>" + Layout.escapeHtml(ENTITY) + " list</caption>\n" +
            "<thead><tr>" + headers + "</tr></thead>\n" +
            "<tbody>" + rows + "</tbody>\n" +
            "</table>" + paginationHtml;
        Map<String, Object> layoutOpts = new LinkedHashMap<>();
        layoutOpts.put("title", ENTITY + "s");
        layoutOpts.put("currentEntity", ENTITY);
        if (user != null) layoutOpts.put("user", user);
        layoutOpts.put("csrf", csrf);
        layoutOpts.put("body", bodyHtml);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", 200);
        out.put("html", Layout.layout(layoutOpts));
        return out;
    }

    // Parse a decoded query value into a non-negative page number, falling back
    // to the default when it is missing or not a usable integer.
    private static int paginationValue(Object raw, int fallback, int min) {
        if (!(raw instanceof String)) return fallback;
        try {
            int n = Integer.parseInt(((String) raw).trim());
            return n < min ? fallback : n;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // Rebuild the list URL for a new offset, preserving every other query
    // parameter (limit, sort, order, filters) verbatim from the raw query so
    // pagination keeps the current view intact.
    private static String pageHref(String rawQuery, int nextOffset) {
        StringBuilder sb = new StringBuilder(BASE).append('?');
        boolean first = true;
        if (rawQuery != null) {
            for (String pair : rawQuery.split("&")) {
                if (pair.isEmpty()) continue;
                int eq = pair.indexOf('=');
                String k = eq < 0 ? pair : pair.substring(0, eq);
                if (k.equals("offset")) continue;
                if (!first) sb.append('&');
                sb.append(pair);
                first = false;
            }
        }
        if (!first) sb.append('&');
        sb.append("offset=").append(nextOffset);
        return sb.toString();
    }
}
