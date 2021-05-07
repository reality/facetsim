@Grapes([
    @Grab(group='org.semanticweb.elk', module='elk-owlapi', version='0.4.3'),
    @Grab(group='net.sourceforge.owlapi', module='owlapi-api', version='4.5.19'),
    @Grab(group='net.sourceforge.owlapi', module='owlapi-apibinding', version='4.5.19'),
    @Grab(group='net.sourceforge.owlapi', module='owlapi-impl', version='4.5.19'),
    @Grab(group='net.sourceforge.owlapi', module='owlapi-parsers', version='4.5.19'),
    @Grab(group='net.sourceforge.owlapi', module='owlapi-distribution', version='4.5.19'),
    @GrabConfig(systemClassLoader=true)
])

import org.semanticweb.owlapi.model.IRI
import org.semanticweb.owlapi.model.parameters.*
import org.semanticweb.elk.owlapi.*
import org.semanticweb.elk.reasoner.config.*
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.io.*
import org.semanticweb.owlapi.owllink.*
import org.semanticweb.owlapi.util.*
import org.semanticweb.owlapi.search.*
import org.semanticweb.owlapi.manchestersyntax.renderer.*
import org.semanticweb.owlapi.reasoner.structural.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import groovyx.gpars.*
import org.codehaus.gpars.*

// Load cluster memberships
println 'Loading clusters ...'
def clusters = [:]
//new File('../data/facet_clust_membership.csv').splitEachLine(',') {
new File('../data/all_clust_membership.csv').splitEachLine(',') {
  //if(!["2","3","5"].contains(it[2])) { return; }
  def cIndex = 2
  if(!clusters.containsKey(it[cIndex])) {
    clusters[it[cIndex]] = [] 
  }
  clusters[it[cIndex]] << it[1].replaceAll('"','')
}

// Loading fMap
def facetList = []
def fMap = [:]
new File('../facets/facet_map.txt').splitEachLine('\t') {
  if(!facetList.contains(it[2])) {
    facetList << it[2]
    fMap[it[2]] = []
  }
  fMap[it[2]] << it[1]
}

// Load patient phenotype profiles
println 'Loading patient phenotypes ...'
def profiles = [:]
new File('../data/annotations_with_modifiers.txt').splitEachLine('\t') {
  def id = it[0].tokenize('.')[0]
  /*if(!fMap['abnormality of the cardiovascular system'].contains(it[1])) {
    return; 
  }*/
  if(!profiles.containsKey(id)) { profiles[id] = [] }
  profiles[id] << it[1]
}

// Loading labels
println 'Loading ontology labels'
def labels = [:]
new File('../data/hpo_labels.txt').splitEachLine('\t') {
  if(!labels.containsKey(it[1])) {
    labels[it[1]] = it[0] 
  }
}

// Load class IC values
println 'Loading information content ...'
def ic = [:]
new File('../data/facet_ic.lst').splitEachLine(',') {
  ic[it[0]] = Float.parseFloat(it[1])
}

// Load the ontology...
println 'Loading and reasoning ontology ...'
def manager = OWLManager.createOWLOntologyManager()
def df = manager.getOWLDataFactory()

def ontology = manager.loadOntologyFromOntologyDocument(new File("../data/hp.owl"))
def progressMonitor = new ConsoleProgressMonitor()
def config = new SimpleConfiguration(progressMonitor)
def elkFactory = new ElkReasonerFactory() // cute
def reasoner = elkFactory.createReasoner(ontology, config)

// So let us first try to look at cluster one 

// collect patient profiles and classes list for cluster 
println 'Calculating cluster explanations ...'

def explainCluster = { cid ->
	def clusterClasses = []
	clusters[cid].each { pid ->
		profiles[pid].each {
			clusterClasses << it
		}
	}
	clusterClasses.unique(true) // computationally inefficient, but should be fast enough for our uses

	def explainers = [:]
	def processClass 
	processClass = { c ->
		if(explainers.containsKey(c)) { return; }
		def ce = df.getOWLClass(IRI.create(c))
		def subclasses = reasoner.getSubClasses(ce, false).collect { n -> n.getEntities().collect { it.getIRI().toString() } }.flatten()
		explainers[c] = [
			ic: ic[c],
			explains: clusters[cid].findAll { pid -> profiles[pid].any { pc -> subclasses.contains(pc) } }.size(),
      oexplains: clusters.collect { lcid, p ->
        if(lcid == cid) { return 0; }
        p.findAll { pid ->
          profiles[pid].any { pc -> subclasses.contains(pc) }
        }.size() 
      }.sum()
		]

		reasoner.getSuperClasses(ce, true).each { n ->
			n.getEntities().each { sc ->
				def strc = sc.getIRI().toString()
				processClass(strc)
			}
		}
	}.trampoline()
	clusterClasses.each { c ->
		processClass(c)
	}

	def maxIc = explainers.collect { k, v -> v.ic }.max()
	def minIc = explainers.collect { k, v -> v.ic }.min()
	def maxEx = explainers.collect { k, v -> v.explains }.max()
	def minEx = explainers.collect { k, v -> v.explains }.min()
  def maxOex = explainers.collect { k, v -> v.oexplains }.max()
  def minOex = explainers.collect { k, v -> v.oexplains }.min()

	explainers = explainers.findAll { k, v -> v.ic }
	explainers = explainers.collect { k, v ->
		v.normIc = (v.ic - minIc) / (maxIc - minIc)
		v.normEx = ((v.explains - minEx) / (maxEx - minEx))
    v.normOex = ((v.oexplains - minOex) / (maxOex - minOex))
		v.score = ((v.normIc * v.normEx) / ((v.normIc) + v.normEx))
    v.oscore = ((1-v.normOex) * v.normEx) / ((1-v.normOex) + v.normEx)
		v.iri = k 
		v
	}

  //explainers = explainers.findAll { it.ic > icCutoff }
	//def sorted = explainers.sort { it.normOex }


  // So ideally I think we instead probably want to kind of step down each value individually and identify some way of identifying the best group of values... thjis will do for now 
  // We can also further introspect by doign the same process to explain clusters pairwise (e.g. if you have two heavily cardiac clusters)
  def stepDown
  stepDown = { e, icCutoff, incCutoff, excCutoff, minExclusion ->
    /*def oexc = exCutoff
    if(oexc < minExclusion) { oexc = minExclusion }*/

    ef = e.findAll {
      it.normIc > icCutoff && it.normEx > incCutoff && (1-it.normOex) > excCutoff
    } 
    if(ef.size() < 2) {
      if(incCutoff == 0 || excCutoff == 0) { return [] }
      return stepDown(e, icCutoff, incCutoff - 0.05, excCutoff - 0.05, minExclusion) 
    } 
    return ef
  }

  println "Cluster $cid (${clusters[cid].size()} patient visits) & {\\bf Exclusion} & {\\bf Inclusion} & {\\bf IC} \\\\"
  explainers = stepDown(explainers, 0.3, 0.95, 0.95, 0.33)
  explainers.sort { -it.normIc }.each {
    println "${labels[it.iri]} (HP:${it.iri.tokenize('_')[1]}) & ${it.normOex.toDouble().round(2)} & ${it.normEx.toDouble().round(2)} & ${it.normIc.toDouble().round(2)} \\\\"
  }
  println "\\hline"

	println ''
}

(1..7).each { i ->
	explainCluster("$i")
}
// TODO create the explanations, then go through and see which pairs have cross-over, explain those in contra-distinction, and add those ones to the explanations
