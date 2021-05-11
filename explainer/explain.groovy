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
new File('../data/facet_clust_membership.csv').splitEachLine(',') {
//new File('../data/all_clust_membership.csv').splitEachLine(',') {
  //if(!["2","3","5"].contains(it[2])) { return; }
  def cIndex = 13
  if(!clusters.containsKey(it[cIndex])) {
    clusters[it[cIndex]] = [] 
  }
  clusters[it[cIndex]] << it[1].replaceAll('"','')
}

// Loading fMap
def facetList = []
def fMap = [:]
def fClassMap = [:]
new File('../facets/facet_map.txt').splitEachLine('\t') {
  if(!facetList.contains(it[2])) {
    facetList << it[2]
    fMap[it[2]] = []
  }
  fMap[it[2]] << it[1]
  if(!fClassMap.containsKey(it[1])) {
    fClassMap[it[1]] = []
  }
  fClassMap[it[1]] << it[2]
}

// Load patient phenotype profiles
println 'Loading patient phenotypes ...'
def profiles = [:]
new File('../data/annotations_with_modifiers.txt').splitEachLine('\t') {
  def id = it[0].tokenize('.')[0]
  if(!fMap['neoplasm'].contains(it[1])) {
    return; 
  }
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

def incs = []
def excs = []

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
			internalIncluded: clusters[cid].findAll { pid -> profiles[pid].any { pc -> subclasses.contains(pc) } },
      externalIncluded: clusters.collect { lcid, p ->
        if(lcid == cid) { return []; }
        p.findAll { pid ->
          profiles[pid].any { pc -> subclasses.contains(pc) }
        }
      }.flatten()
		]
    explainers[c].inclusion = explainers[c].internalIncluded.size()
    explainers[c].exclusion = explainers[c].externalIncluded.size()

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
	def maxEx = explainers.collect { k, v -> v.inclusion }.max()
	def minEx = explainers.collect { k, v -> v.inclusion }.min()
  def maxOex = explainers.collect { k, v -> v.exclusion }.max()
  def minOex = explainers.collect { k, v -> v.exclusion }.min()

	explainers = explainers.findAll { k, v -> v.ic }
	explainers = explainers.collect { k, v ->
		v.nIc = (v.ic - minIc) / (maxIc - minIc)
		v.nInclusion = ((v.inclusion - minEx) / (maxEx - minEx))
    v.nExclusion = 1 - ((v.exclusion - minOex) / (maxOex - minOex))
		v.iri = k 
		v
	}

  def MAX_IC = 0.6
  def MIN_IC = 0.3
  def MAX_INCLUSION = 0.95
  def MIN_INCLUSION = 0.3
  def MAX_EXCLUSION = 0.95
  def MIN_EXCLUSION = 0.3
  def MAX_TOTAL_INCLUSION = 0.95
  def STEP = 0.05

  def stepDown
  stepDown = { e, icCutoff, exclusionCutoff, inclusionCutoff, totalInclusionCutoff ->
    ef = e.findAll {
      //println "comparing $it.normIc and $icCutoff for IC. comparing $it.nExclusion and $exclusionCutoff for exclusion"
      it.nIc >= icCutoff && it.nExclusion >= exclusionCutoff && it.nInclusion >= inclusionCutoff
    } 
    //println ef
    def totalCoverage = ((ef.collect { it.internalIncluded }.flatten().unique(false).size()) / clusters[cid].size()) * 100
    def totalExclusion = 1-(((ef.collect { it.internalExcluded }.flatten().unique(false).size()) / (clusters.collect {k,v->v.size()}.sum() - clusters[cid].size())) * 100)
    //println "DEBUG: running with ic cutoff: $icCutoff exclusion cutoff: $exclusionCutoff inclusion cutoff: $inclusionCutoff total: coverage: $totalCoverage/$totalInclusionCutoff"
    if(totalCoverage <= (totalInclusionCutoff*100)) {
      if(inclusionCutoff <= MIN_INCLUSION) {
        if(icCutoff >= MIN_IC) {
          return stepDown(e, icCutoff - STEP, MAX_EXCLUSION, MAX_INCLUSION, totalInclusionCutoff)
        } else {
          return stepDown(e, MAX_IC, MAX_EXCLUSION, MAX_INCLUSION, totalInclusionCutoff - STEP)
        }
      } else {
        def newExclusion = exclusionCutoff - STEP
        if(newExclusion < MIN_EXCLUSION) { newExclusion = MIN_EXCLUSION  }
        return stepDown(e, icCutoff, newExclusion, inclusionCutoff - STEP, totalInclusionCutoff)
      }
    } 
    return [ef, totalCoverage, totalExclusion]
  }.trampoline()

  def sd = stepDown(explainers, MAX_IC, MAX_EXCLUSION, MAX_INCLUSION, MAX_TOTAL_INCLUSION)
  explainers = sd[0]
  println "{\\bf Cluster $cid (${clusters[cid].size()} patient visits, overall inclusivity: ${sd[1].toDouble().round(2)})} & {\\bf Exclusion} & {\\bf Inclusion} & {\\bf IC} \\\\"
  explainers.sort { -it.nIc }.each {
    incs << it.nExclusion
    excs << it.nInclusion
    println "${labels[it.iri]} (HP:${it.iri.tokenize('_')[1]}) & ${it.nExclusion.toDouble().round(2)} & ${it.nInclusion.toDouble().round(2)} & ${it.nIc.toDouble().round(2)} \\\\"
  }

  println "\\hline"

	println ''

  return explainers
}

def finalExplanations = []
(1..4).each { i ->
	finalExplanations << explainCluster("$i")
}

def overallFacetMentions = [:]
facetList.each {
  overallFacetMentions[it] = 0
}

finalExplanations.eachWithIndex { cexps, z ->
  println "Cluster $z & & \\\\"
  facetList.each { facet ->
    if(facet == 'phenotypic abnormality') { return; }
    def percentage = 0
    try {
      percentage = cexps.findAll { fClassMap[it.iri].contains(facet) }.size() / cexps.size()
    } catch(e){} 
    cexps.each { if(fClassMap[it.iri].contains(facet)) { overallFacetMentions[facet]++ } }
    if(percentage != 0) {
      println "& $facet & ${percentage.toDouble().round(2)} \\\\" 
    }
  }
}

// i knoew, it's real lazy
def pat = []
def ann = []
def app = []
overallFacetMentions.each { k, v ->
  if(k != 'phenotypic abnormality') {
    pat << profiles.findAll { id, codes -> codes.any { 
      if(fClassMap.containsKey(it)) {
        fClassMap[it].contains(k)}
      }
    }.size()
    ann << profiles.collect { id, codes -> codes.findAll { 
      if(fClassMap.containsKey(it)) {
        fClassMap[it].contains(k) 
      } 
    } }.flatten().size()

    app << v
  }
}

println "patcounts = c(${pat.join(',')})"
println "anncounts = c(${ann.join(',')})"
println "appcounts = c(${app.join(',')})"

println "${incs.sum() / incs.size()}"
println "${excs.sum() / excs.size()}"
// TODO create the explanations, then go through and see which pairs have cross-over, explain those in contra-distinction, and add those ones to the explanations
