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
new File('../data/all_clust_membership.csv').splitEachLine(',') {
  if(!clusters.containsKey(it[2])) {
    clusters[it[2]] = [] 
  }
  clusters[it[2]] << it[1].replaceAll('"','')
}

// Load patient phenotype profiles
println 'Loading patient phenotypes ...'
def profiles = [:]
new File('../data/annotations_with_modifiers.txt').splitEachLine('\t') {
  def id = it[0].tokenize('.')[0]
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

  println "cluster $cid"
	println "members: ${clusters[cid].size()}"

  // So ideally I think we instead probably want to kind of step down each value individually and identify some way of identifying the best group of values... thjis will do for now 
  // We can also further introspect by doign the same process to explain clusters pairwise (e.g. if you have two heavily cardiac clusters)
  def stepDown
  stepDown = { e, icCutoff, exCutoff, minExclusion ->
    def oexc = exCutoff
    if(oexc < minExclusion) { oexc = minExclusion }

    ef = e.findAll {
      it.normIc > icCutoff && it.normEx > exCutoff && (1-it.normOex) > oexc
    } 
    if(ef.size() < 2) {
      return stepDown(e, icCutoff, exCutoff - 0.05, minExclusion) 
    } 
    return ef
  }

  explainers = stepDown(explainers, 0.3, 0.95, 0.65)
  explainers.sort { -it.normIc }.each {
    println "${labels[it.iri]}\t$it.iri\texclusivity score:${it.normOex}\tinclusivity score:${it.normEx}\tic:${it.normIc}"
  }

	println ''
}

(1..7).each { i ->
	explainCluster("$i")
}

// TODO so we can also identify a minimum coverage it needs to have, and a er 
// TODO we can also develop a measure of cluster health, by creating a score of how much cross-over there is between your explanatory variables...
