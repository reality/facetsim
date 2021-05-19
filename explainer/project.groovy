def allClusters = [:]

new File('../data/all_clust_membership.csv').splitEachLine(',') {
  def cIndex = 2
if(it[cIndex].indexOf('clus') != -1) { return; }
  if(!allClusters.containsKey(it[cIndex])) {
    allClusters[it[cIndex]] = [] 
  }
  allClusters[it[cIndex]] << it[1].replaceAll('"','')
}

def facetClusters = [:]
new File('../data/facet_clust_membership.csv').splitEachLine(',') {
  def cIndex = 13
if(it[cIndex].indexOf('neoplasm') != -1) { return; }
  if(!facetClusters.containsKey(it[cIndex])) {
    facetClusters[it[cIndex]] = [] 
  }
  facetClusters[it[cIndex]] << it[1].replaceAll('"','')
}

facetClusters['allCancer'] = facetClusters['1'] + facetClusters['2'] + facetClusters['3'] + facetClusters['4'] + facetClusters['5'] + facetClusters['6'] + facetClusters['7']

facetClusters.each { k, v ->
  println "facet cluster $k"
  allClusters.each { ak, av ->
    def innit = av.findAll { v.contains(it) }.size()
    println "  in facet cluster $ak: ${(innit / v.size()) * 100}"
  }
}

