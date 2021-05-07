def facetList = []
def fMap = [:]
new File('../facets/facet_map.txt').splitEachLine('\t') {
  if(it[2] == 'phenotypic abnormality') { return; }
  if(!facetList.contains(it[2])) {
    facetList << it[2]
  }
  fMap[it[1]] = it[2]
}

facetList << 'all'

def aCounts = [:]
def a1Counts = [:]
def fCounts = [:]

def full_plist = []

def pMap = [:]

new File('../data/annotations_with_modifiers.txt').splitEachLine('\t') {
  def facet = fMap[it[1]]
  if(!aCounts.containsKey(facet)) {
    aCounts[facet] = 0 
  }
  aCounts[facet]++
  if(!a1Counts.containsKey(facet)) {
    a1Counts[facet] = [] 
  }
  if(!a1Counts[facet].contains(it[0])) {
    a1Counts[facet] << it[0]
  }

  if(!fCounts.containsKey(facet)) {
    fCounts[facet] = [:]
  }
  if(!fCounts[facet].containsKey(it[0])) {
    fCounts[facet][it[0]] = 0 
  }
  fCounts[facet][it[0]]++

  if(!full_plist.contains(it[0])) {
  full_plist << it[0] 
  }

  if(!pMap.containsKey(it[0])) {
    pMap[it[0]] = [] 
  }
  if(!pMap[it[0]].contains(facet)) {
    pMap[it[0]] << facet 
  }


  // i know, cbf
  facet = 'all'
  if(!aCounts.containsKey(facet)) {
    aCounts[facet] = 0 
  }
  aCounts[facet]++
  if(!a1Counts.containsKey(facet)) {
    a1Counts[facet] = [] 
  }
  if(!a1Counts[facet].contains(it[0])) {
    a1Counts[facet] << it[0]
  }

  if(!fCounts.containsKey(facet)) {
    fCounts[facet] = [:]
  }
  if(!fCounts[facet].containsKey(it[0])) {
    fCounts[facet][it[0]] = 0 
  }
  fCounts[facet][it[0]]++

  if(!full_plist.contains(it[0])) {
  full_plist << it[0] 
  }

}

facetList.each { f ->
  // bad code logic
  if(!aCounts.containsKey(f)) { aCounts[f] = 0 }
  if(!a1Counts.containsKey(f)) { a1Counts[f] = [] }
  if(!fCounts.containsKey(f)) {
    fCounts[f] = [:]
  }
  def tavg = (fCounts[f].collect { k, v -> v }.sum() / full_plist.size()).toDouble().round(3)
  def favg = (fCounts[f].collect { k, v -> v }.sum() / fCounts[f].size()).toDouble().round(3)
  println "$f & ${a1Counts[f].size()} & ${favg} & ${tavg} & ${aCounts[f]}\\\\"
}

println pMap.collect { k, v -> v.size() }.sum() / pMap.size()
