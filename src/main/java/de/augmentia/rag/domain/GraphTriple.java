package de.augmentia.rag.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GraphTriple(
    @JsonAlias({"source", "src"}) String source,
    @JsonAlias({"relation", "rel"}) String relation,
    @JsonAlias({"target", "tgt"}) String target,
    @JsonAlias({"description", "desc"}) String description
) {}
