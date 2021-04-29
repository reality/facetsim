# Facet Similarity Experimental

Experiments with faceted similarity. Ask me for the data files ...

## Generating similarity matrix

You should just be able to source the similarity matrices from me. But in case you want to create it yourself. You will need to source the following files:

* data/annotations_with_modifiers.txt
* data/hp.owl
* data/sampled_patient_visits.txt

You can then run:

```bash
cd similarity
groovy run_similarity.groovy ../data/annotations_with_modifiers.txt false hp.owl
```

For me it took about 12 hours. This will create *data/facet_matrix.lst*. 

You can then run the following to populate *data/facet_matrices* with actual matrix-format files that can easily be read into that data-type in R (you should still be in the similarity subdirectory):

```bash
groovy transform.groovy
```

## Files 

### Data

* `data/sampled_patient_visits.csv` - 1,000 sampled MIMIC patient visits. The ID is a concatenation of the patient ID and the visit ID. Also contains ICD-9 primary diagnosis, and mapped DOID term.
* `data/annotations_with_modifiers.txt` - HPO annotations of sampled MIMIC patient text narrative, created by Komenti.
* `data/hp.owl` - The Human Phenotype Ontology, sourced from http://purl.obolibrary.org/obo/hp.owl
* `data/annot.tsv* - Corpus file generated and used by SML when generating IC for similarity scoring.
* `data/facet_matrix.lst` - The direct output of the similarity script, representing several similarity matrices. There is one line for each pairwise patient visit comparison, with an overall similarity score, and a score for each facet. Also contains a logical column that identifies whether the two patient visits share a matching primary diagnosis.
* `data/facet_matrices/` - This directory contains the output of `transform.groovy`, with one patient visit similarity matrix per HPO facet.

### Facets

* `facets/create_script.groovy` - Outputs a script to create a facet map from Komenti. Its output is saved in `facets/query_facets.sh`
* `facets/query_facets` - A shell script consisting of a series of Komenti commands that retrieves a map of facet membership.
* `facets/facets.txt` - A list of HPO facets, i.e. direct subclasses of `Phenotypic abnormality`, in Komenti vocabulary format. The labels are those used in the similarity matrix output.
* `facets/facet_map.txt` - A list of HPO terms and their facet membership, in Komenti vocabulary format. This is used by the similarity program to identify facet membership.

### Similarity

* `similarity/run_similarity.groovy` - Script to do facet-based similarity comparisons (see info above). Currently configured with Resnik+BMA, with Resnik-IC.
* `similarity/transform.groovy` - Uses the 'flat' matrix output of `run_similarity.groovy` to create per-facet matrices that can easily be loaded into R (see info above).

### Evaluation

* `evaluation/perf.R` - R script to investigate facet and meta-facet performance for predicting shared primary diagnosis.
* `evaluation/cluster.R` - R script explorin facet-based clustering. 
