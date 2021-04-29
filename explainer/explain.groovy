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
			explains: clusters[cid].collect { pid -> profiles[pid].findAll { pc -> subclasses.contains(pc) }.size() }.sum()
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

	explainers = explainers.findAll { k, v -> v.ic }
	explainers = explainers.collect { k, v ->
		def normIc = (v.ic - minIc) / (maxIc - minIc)
		def normEx = (v.explains - minEx) / (maxEx - minEx)
		v.score = (normIc / 2) + (normEx / 2)
		v.iri = k 
		v
	}

	def sorted = explainers.sort { -it.score }

	println clusters[cid].size()
	println sorted.subList(0,10)
	println ''
}

(1..7).each { i ->
	explainCluster("$i")
}

// TODO so we can also identify a minimum coverage it needs to have, and a er 
// TODO we can also develop a measure of cluster health, by creating a score of how much cross-over there is between your explanatory variables...
