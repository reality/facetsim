def idx = 4
new File('../facets/facets.txt').splitEachLine('\t') {
  println "groovy transform.groovy ${idx} > \"../data/facet_matrices/${it[0]}.mtx\" && \\"
  idx++
}
