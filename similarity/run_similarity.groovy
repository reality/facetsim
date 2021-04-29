@Grab(group='org.semanticweb.elk', module='elk-owlapi', version='0.4.3')
@Grab(group='net.sourceforge.owlapi', module='owlapi-api', version='4.2.5')
@Grab(group='net.sourceforge.owlapi', module='owlapi-apibinding', version='4.2.5')
@Grab(group='net.sourceforge.owlapi', module='owlapi-impl', version='4.2.5')
@Grab(group='com.github.sharispe', module='slib-sml', version='0.9.1')

import groovyx.gpars.*
import org.codehaus.gpars.*
import java.util.concurrent.*
 
import slib.graph.algo.utils.GAction;
import slib.graph.algo.utils.GActionType;

import org.openrdf.model.URI;
import slib.graph.algo.accessor.GraphAccessor;
//import slib.graph.algo.extraction.utils.GAction;
//import slib.graph.algo.extraction.utils.GActionType;
import slib.graph.algo.validator.dag.ValidatorDAG;
import slib.graph.io.conf.GDataConf;
import slib.graph.io.conf.GraphConf;
import slib.graph.io.loader.GraphLoaderGeneric;
import slib.graph.io.util.GFormat;
import slib.graph.model.graph.G;
import slib.graph.model.impl.graph.memory.GraphMemory;
import slib.graph.model.impl.repo.URIFactoryMemory;
import slib.graph.model.repo.URIFactory;
import slib.sml.sm.core.engine.SM_Engine;
import slib.sml.sm.core.metrics.ic.utils.IC_Conf_Topo;
import slib.sml.sm.core.metrics.ic.utils.ICconf;
import slib.graph.algo.extraction.utils.*
import slib.sglib.io.loader.*
import slib.sml.sm.core.metrics.ic.utils.*
import slib.sml.sm.core.utils.*
import slib.sglib.io.loader.bio.obo.*
import org.openrdf.model.URI
import slib.graph.algo.extraction.rvf.instances.*
import slib.sglib.algo.graph.utils.*
import slib.utils.impl.Timer
import slib.graph.algo.extraction.utils.*
import slib.graph.model.graph.*
import slib.graph.model.repo.*
import slib.graph.model.impl.graph.memory.*
import slib.sml.sm.core.engine.*
import slib.graph.io.conf.*
import slib.graph.model.impl.graph.elements.*
import slib.graph.algo.extraction.rvf.instances.impl.*
import slib.graph.model.impl.repo.*
import slib.graph.io.util.*
import slib.graph.io.loader.*

import slib.sml.sm.core.metrics.ic.utils.IC_Conf_Corpus;
import slib.sml.sm.core.utils.SMConstants;
import slib.sml.sm.core.utils.SMconf;
import slib.utils.ex.SLIB_Exception;
import slib.utils.impl.Timer;

def aFile = args[0]
def rmneg = args[1] == 'true'
def oFile = args[2]

def patient_visit_diagnoses = [:]
def pcounts = [:]

def facetList = []
def fMap = [:]
new File('./facets/facet_map.txt').splitEachLine('\t') {
  if(!facetList.contains(it[2])) {
    facetList << it[2]
    fMap[it[2]] = []
  }
  fMap[it[2]] << it[1]
}

new File('./sampled_patient_visits.csv').splitEachLine('\t') {
it[1] = it[1]//.substring(0,3)
  patient_visit_diagnoses[it[0]] = [it[1]]
  if(!pcounts[it[1]]) { pcounts[it[1]] = 0 }
  pcounts[it[1]]++
  println patient_visit_diagnoses[it[0]]
}

ConcurrentHashMap aMap = [:]

def cList = []

def aFileContent = []
new File(aFile).splitEachLine('\t') { aFileContent << it }

def t = 0
aFileContent.each {
  if(it[0] && it[1]) {
  it[0] = it[0].tokenize('.')[0]
  if(patient_visit_diagnoses.containsKey(it[0])) {
    if(rmneg && (it[5].indexOf('unc') != -1 || it[5].indexOf('neg') != -1)) { println 'fye' ; return ;}
    if(!aMap.containsKey(it[0])) {
      aMap[it[0]] = []
    }
    it[1] = it[1].replace('<', '').replace('>', '').tokenize('/').last()
    def z = it[1].tokenize('_')
    it[1] = z[0]+':' + z[1]

    if(!aMap[it[0]].contains(it[1])) {
      aMap[it[0]] << it[1]
    }

    cList << it[1]
    println "${++t}/${aFileContent.size()}"
    }
  }
}

println 'writing the annotation file now'
def sWriter = new BufferedWriter(new FileWriter('../data/annot.tsv'))
def oo = ''
def y = 0
aMap.each { a, b ->
  println "(${++y}/${aMap.size()})"
  if(!b.any{ it.indexOf('txt') != -1}) {
    sWriter.write('http://reality.rehab/ptvis/' + a + '\t' + b.join(';') + '\n')
  }
}
sWriter.flush()
sWriter.close()
println 'done'

cList = cList.unique()

URIFactory factory = URIFactoryMemory.getSingleton()

def ontoFile = oFile
def graphURI = factory.getURI('http://HP/')
factory.loadNamespacePrefix("HP", graphURI.toString());

G graph = new GraphMemory(graphURI)

def dataConf = new GDataConf(GFormat.RDF_XML, ontoFile)
def actionRerootConf = new GAction(GActionType.REROOTING)
actionRerootConf.addParameter("root_uri", "http://purl.obolibrary.org/obo/HP_0000001"); // phenotypic abnormality
//actionRerootConf.addParameter("root_uri", "DOID:4"); // phenotypic abnormality

def gConf = new GraphConf()
gConf.addGDataConf(dataConf)
gConf.addGAction(actionRerootConf)
//def gConf = new GraphConf()
//gConf.addGDataConf(dataConf)
def annot = '../data/annot.tsv'
gConf.addGDataConf(new GDataConf(GFormat.TSV_ANNOT, annot));

GraphLoaderGeneric.load(gConf, graph)

println graph.toString()

def roots = new ValidatorDAG().getTaxonomicRoots(graph)
println roots

def icConf = new IC_Conf_Corpus(SMConstants.FLAG_IC_ANNOT_RESNIK_1995)
//def icConf = new IC_Conf_Topo(SMConstants.FLAG_ICI_ZHOU_2008)
//def icConf = new IC_Conf_Topo(SMConstants.FLAG_ICI_SANCHEZ_2011)
def smConfPairwise = new SMconf(SMConstants.FLAG_SIM_PAIRWISE_DAG_NODE_RESNIK_1995, icConf)
//def smConfPairwise = new SMconf(SMConstants.FLAG_SIM_PAIRWISE_DAG_NODE_LIN_1998, icConf)
//def smConfGroupwise = new SMconf(SMConstants.FLAG_SIM_GROUPWISE_AVERAGE, icConf)
def smConfGroupwise = new SMconf(SMConstants.FLAG_SIM_GROUPWISE_BMA, icConf)
// FLAG_SIM_GROUPWISE_AVERAGE_NORMALIZED_GOSIM

//def smConfPairwise = new SMconf(SMConstants.FLAG_SIM_PAIRWISE_DAG_NODE_JIANG_CONRATH_1997_NORM , icConf)


def z = 0

def outWriter = new BufferedWriter(new FileWriter('../data/facet_matrix.lst'), 1024 * 1024 * 1024)
if(rmneg) {
outWriter = new BufferedWriter(new FileWriter('../data/facet_matrix_noneg.lst'), 1024 * 1024 * 1024)
}

def engine = new SM_Engine(graph)

cList = cList.unique()


def getURIfromTerm = { term ->
    term = term.tokenize(':')
    return factory.getURI("http://purl.obolibrary.org/obo/HP_" + term[1])
}

outWriter.write('g1,g2,all_score,i,'+facetList.join(',')+',match\n')

def rrs=[]
def aps=[]
aMap.each { g1, u1 ->
  println "(${++z}/${aMap.size()})"
  def aList = []
  aMap.each { g2, u2 ->
    if(g1 == g2) { return; }
    def match = patient_visit_diagnoses[g1][0] == patient_visit_diagnoses[g2][0]

    def line = [g1,g2]

    // Get the overall score
    try {
      line << engine.compare(smConfGroupwise, smConfPairwise,
                                    u1.collect { 
                                      getURIfromTerm(it)
                                     }.findAll { graph.containsVertex(it) }.toSet(), 
                                    u2.collect { 
                                      getURIfromTerm(it)
                                    }.findAll { graph.containsVertex(it) }.toSet())

    } catch(e) { line << 0 }

    // Add score for each facet
    facetList.each { f ->
     try {
      line << engine.compare(smConfGroupwise, smConfPairwise,
                                    u1.collect { 
                                      getURIfromTerm(it)
                                     }.findAll { fMap[f].contains(it.toString()) && graph.containsVertex(it) }.toSet(), 
                                    u2.collect { 
                                      getURIfromTerm(it)
                                    }.findAll { fMap[f].contains(it.toString()) && graph.containsVertex(it) }.toSet())

      } catch(e) { line << 0 }  
    }

    line << match
    aList << line
  }

  aList = aList.toSorted { it[2] }.reverse() //[0..10]
  aList.eachWithIndex { it, i -> 
    def out = it.subList(0, 3).join(',') + ',' + (i+1) + ',' + it.subList(3, it.size()).join(',') + '\n'
    print out
    outWriter.write(out)
  }
}

outWriter.flush()
outWriter.close()
