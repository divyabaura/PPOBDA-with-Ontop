# PPOBDA-with-Ontop
Implementation of CQE using the capabilities of open source OBDA system, Ontop .

Expanding policy with respect to the TBox, Ontop performs query rewriting operations with mappings, necessitating the existence of a data source. To address this, a Java function DummyMappingGenerator or DirectMappingGenerator, simulating the data source by establishing one-to-one mappings from a dummy database to the ontology.

This simulation activates the Ontop reasoner. Subsequently, the java function PolicyExpander function takes the policies P as user queries, activating the Ontop reasoner by providing Direct Mapping as a connection between the data source and the ontology. This process results in policy rewriting with respect to the ontology.

The rewritten queries, serving as expanded policies with the ontology, are then inputted into the PPOBDAMapping Compiler function, which redefines each predicate (including negations for atoms that violate policy) as a SPARQL query. These SPARQL queries, along with the ontology, are then processed by a java function PolicyEmbed function, which converts these into SQL queries using the Ontop query engine. Subsequently, a new mapping is created with these SQL queries as the source for each respective predicate in the target of output OBDA file.

This process effectively encodes mappings with the existing policy.
