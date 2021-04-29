def scoreCol = Integer.parseInt(args[0])
def mtx = [:]
new File("../data/facet_matrix.lst").splitEachLine(','){
  if(it[0] == 'g1') { return; }
  if(!mtx.containsKey(it[0])) { mtx[it[0]] = [:] }
  def score = it[scoreCol]
  mtx[it[0]][it[1]] = score
  if(!mtx.containsKey(it[1])) { mtx[it[1]] = [:] }
  mtx[it[1]][it[0]] = score
}

def keys = mtx.keySet().asList()
println keys.join(',')
keys.each { k1 ->
  def line = []
  keys.each { k2 ->
    if(k1 == k2) {
      line << '0' 
    } else if(mtx[k1].containsKey(k2)) { 
      line << mtx[k1][k2] 
    } else {
      line << '0'
    }
  }
  println k1 + ',' + line.join(',')
}

