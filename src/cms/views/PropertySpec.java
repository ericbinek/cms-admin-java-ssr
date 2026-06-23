package cms.views;

import java.util.List;

/**
 * Typed metadata for one entity property the views render. A sealed hierarchy
 * so form and detail rendering can branch over property shapes with an
 * exhaustive pattern-matching switch instead of reading an untyped Map
 * descriptor and casting its entries.
 */
public sealed interface PropertySpec {

    String name();

    Cardinality cardinality();

    boolean required();

    enum Cardinality { ONE, MANY }

    record Scalar(String name, String use, Cardinality cardinality, boolean required, Integer maxLength, boolean multiline) implements PropertySpec {}

    record Enumerated(String name, List<String> values, Cardinality cardinality, boolean required) implements PropertySpec {}

    record Ref(String name, List<String> targets, Cardinality cardinality, boolean required) implements PropertySpec {}

    record Embed(String name, String use, Cardinality cardinality, boolean required) implements PropertySpec {}
}
