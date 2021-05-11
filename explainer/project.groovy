def allClusters = [:]

new File('../data/all_clust_membership.csv').splitEachLine(',') {
  def cIndex = 2
if(it[cIndex].indexOf('clust') != -1) { return; }
  if(!allClusters.containsKey(it[cIndex])) {
    allClusters[it[cIndex]] = [] 
  }
  allClusters[it[cIndex]] << it[1].replaceAll('"','')
}

def facetClusters = [:]
new File('../data/facet_clust_membership.csv').splitEachLine(',') {
  def cIndex = 3
  if(!facetClusters.containsKey(it[cIndex])) {
    facetClusters[it[cIndex]] = [] 
  }
  facetClusters[it[cIndex]] << it[1].replaceAll('"','')
}

allClusters.each { k, v ->
  println "overall cluster $k"
  facetClusters.each { ak, av ->
    def innit = av.findAll { v.contains(it) }.size()
    println "  in facet cluster $ak: ${(innit / v.size()) * 100}"
  }
}
