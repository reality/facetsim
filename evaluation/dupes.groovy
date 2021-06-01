def pids = []
new File('../data/sampled_patient_visits.csv').splitEachLine('\t') {
  pids << it[0].tokenize('_')[0]
}

println pids.unique(false).size()
