package cms.views.WebPage;

import cms.AdminApiClient;
import cms.views.Layout;
import cms.views.PropertySpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DetailView {

    public static final String ENTITY = "WebPage";
    public static final String BASE = "/web-pages";
    public static final List<PropertySpec> PROPERTIES = new ArrayList<>();
    static {
        PROPERTIES.add(new PropertySpec.Scalar("headline", "Text", PropertySpec.Cardinality.ONE, true));
        PROPERTIES.add(new PropertySpec.Scalar("description", "Text", PropertySpec.Cardinality.ONE, false));
        PROPERTIES.add(new PropertySpec.Scalar("text", "Text", PropertySpec.Cardinality.ONE, false));
        PROPERTIES.add(new PropertySpec.Ref("author", List.of("Person"), PropertySpec.Cardinality.ONE, false));
        PROPERTIES.add(new PropertySpec.Ref("primaryImageOfPage", List.of("ImageObject"), PropertySpec.Cardinality.ONE, false));
        PROPERTIES.add(new PropertySpec.Ref("isPartOf", List.of("WebPage"), PropertySpec.Cardinality.ONE, false));
        PROPERTIES.add(new PropertySpec.Scalar("datePublished", "DateTime", PropertySpec.Cardinality.ONE, false));
        PROPERTIES.add(new PropertySpec.Scalar("dateModified", "DateTime", PropertySpec.Cardinality.ONE, false));
        PROPERTIES.add(new PropertySpec.Scalar("dateCreated", "DateTime", PropertySpec.Cardinality.ONE, false));
        PROPERTIES.add(new PropertySpec.Scalar("url", "URL", PropertySpec.Cardinality.ONE, false));
        PROPERTIES.add(new PropertySpec.Embed("inLanguage", "Language", PropertySpec.Cardinality.ONE, false));
        PROPERTIES.add(new PropertySpec.Enumerated("creativeWorkStatus", List.of("Draft", "Pending", "Published", "Archived"), PropertySpec.Cardinality.ONE, false));
    }

    private DetailView() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> render(Map<String, Object> opts) {
        AdminApiClient api = (AdminApiClient) opts.get("api");
        Object user = opts.get("user");
        Object csrf = opts.get("csrf");
        String id = (String) opts.get("id");
        AdminApiClient.Response r = api.get(ENTITY, id);
        if (r.status == 404) return Layout.errorPage(404, ENTITY + " not found.", user);
        if (r.status != 200) {
            String msg = "Failed to load.";
            if (r.body instanceof Map && ((Map<?, ?>) r.body).get("message") instanceof String) msg = (String) ((Map<?, ?>) r.body).get("message");
            return Layout.errorPage(r.status, msg, user);
        }
        Map<String, Object> item = (Map<String, Object>) r.body;
        StringBuilder rows = new StringBuilder();
        for (PropertySpec p : PROPERTIES) {
            rows.append("<dt>").append(Layout.escapeHtml(p.name())).append("</dt>")
                .append("<dd>").append(Layout.formatValue(item.get(p.name()), p)).append("</dd>");
        }
        String meta =
            "<dt>id</dt><dd><code>" + Layout.escapeHtml(item.get("id")) + "</code></dd>" +
            "<dt>dateCreated</dt><dd><time datetime=\"" + Layout.escapeHtml(item.getOrDefault("dateCreated", "")) + "\">" + Layout.escapeHtml(item.getOrDefault("dateCreated", "")) + "</time></dd>" +
            "<dt>dateModified</dt><dd><time datetime=\"" + Layout.escapeHtml(item.getOrDefault("dateModified", "")) + "\">" + Layout.escapeHtml(item.getOrDefault("dateModified", "")) + "</time></dd>";
        String idEsc = Layout.escapeHtml(item.get("id"));
        String body =
            "<article>\n" +
            "<dl>" + rows + meta + "</dl>\n" +
            "<p>\n" +
            "<a href=\"" + BASE + "/" + idEsc + "/edit\">Edit</a> ·\n" +
            "<a href=\"" + BASE + "/" + idEsc + "/delete\">Delete</a> ·\n" +
            "<a href=\"" + BASE + "\">Back to list</a>\n" +
            "</p>\n" +
            "</article>";
        Map<String, Object> opts2 = new LinkedHashMap<>();
        opts2.put("title", Layout.displayName(item, ENTITY));
        opts2.put("currentEntity", ENTITY);
        if (user != null) opts2.put("user", user);
        opts2.put("csrf", csrf);
        opts2.put("body", body);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", 200);
        out.put("html", Layout.layout(opts2));
        return out;
    }
}
