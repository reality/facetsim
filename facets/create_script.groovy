println "touch facet_map.txt"
new File('./facets.txt').splitEachLine('\t') {
  println "komenti query -q \"<${it[1]}>\" --override-group \"${it[0]}\" --query-type subeq --class-mode --out facet_map.txt --append --ontology HP"
}
