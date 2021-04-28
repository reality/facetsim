new File('./facets.txt').splitEachLine('\t') {
  println "komenti query -q \"<${it[1]}>\" --override-group ${it[0]} --query-type subeq --class-mode --out facet_map.txt"
}
