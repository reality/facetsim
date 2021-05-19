library(similecari)
library(readr)
library(dynamicTreeCut)
library(cluster)
library(factoextra)
library(rlist)
library(gridExtra)

processMatrix <- function(fname) {
  set.seed(1337)
  mtx <- read.csv(fname, row.names = 1, sep = ",", header = TRUE)
  mtx <- data.matrix(mtx)

  # what the f****
  colnames(mtx) <- gsub(x = colnames(mtx), pattern = "\\.", replacement = ":")
  colnames(mtx) <- gsub(x = colnames(mtx), pattern = "X", replacement = "")

  tomMtx <- calculateDistanceMatrix(mtx)

  bestSil <- -100
  bestX <- 0
  bestModel <- 0
  for(x in 3:20) {
    gc()
    km <- kmeans(dist(tomMtx), centers=x, iter.max=200000, nstart=2)
    ss <- silhouette(km$cluster,dist(tomMtx))
    score <- mean(ss[, 3])
    if(score > bestSil && score <= 1 && score >= -1) {
      bestSil <- score
      bestModel <- km
      bestX <- x
    }
  }

  return(list(
    "bestSil" = bestSil,
    "bestX" = bestX,
    "km" = bestModel,
    "mtx" = mtx,
    "omtx" = mtx
  ))
  #print(bestSil)
  #print(bestX)
  #fviz_cluster(bestModel, data = tomMtx)
}
calculateDistanceMatrix <- function(simMatrix) {
  # create adjacency matrix, weighted with exponential power to minimize low similarity
  # distances
  adjacencyMatrix <- WGCNA::adjacency(simMatrix, type = "distance", power = 2, corFnc = "I",
                                      corOptions = "", distFnc = "I", distOptions = "")
  return(adjacencyMatrix)
  TOM <- .Call("tomSimilarityFromAdj_call", as.matrix(adjacencyMatrix),
               as.integer(1), as.integer(0), as.integer(1),
               as.integer(1), as.integer(1),
               as.integer(1), as.integer(1), PACKAGE = "WGCNA")

  # create dissimilarity matrix used in clustering
  dissTOM = 1-TOM

  # clean up RAM
  WGCNA::collectGarbage()

  return(dissTOM)
}
l <- processMatrix(paste("~/simsim/data/facet_matrices/neoplasm.mtx", sep = ""))
fviz_cluster(l$km, data=dist(l$mtx))

set.seed(1337)
l <- processMatrix(paste("~/simsim/data/facet_matrices/all.mtx", sep = ""))
  ss <- silhouette(l$km$cluster, dist(l$omtx))
  mean(ss[, 3])
rownames(l$mtx) <- NULL
fviz_cluster(l$km, data=dist(l$mtx), main="Overall cluster visualisation", labelsize = 0)


ss <- silhouette(l$km$cluster, l$omtx)
mean(ss[, 3])

mtx <- read.csv("~/simsim/data/facet_matrices/neoplasm.mtx", row.names = 1, sep = ",", header = TRUE)
mtx <- data.matrix(mtx)
colnames(mtx) <- gsub(x = colnames(mtx), pattern = "X", replacement = "")
km <- kmeans(dist(mtx), centers = 13, nstart=25)
fviz_cluster(km, data=dist(mtx))

silhouette_score <- function(k){
  km <- kmeans(mtx, centers = k, nstart=25)

}
k <- 2:10
avg_sil <- sapply(k, silhouette_score)
plot(k, type='b', avg_sil, xlab='Number of clusters', ylab='Average Silhouette Scores', frame=FALSE)

set.seed(1337)


colnames(mtx) <- gsub(x = colnames(mtx), pattern = "\\.", replacement = ":")
colnames(mtx) <- gsub(x = colnames(mtx), pattern = "X", replacement = "")

mtx <- BBmisc::normalize(mtx, method= "range", range = c(0,1))
  km <- kmeans(as.dist(mtx), centers=4, iter.max=200000)
  ss <- silhouette(km$cluster, mtx)
  mean(ss[,3])
fviz_cluster(l$km, data = dist(l$mtx)) +
# Look at all
k <- kmeans(as.dist(tomMtx), centers=7)
sil <- silhouette(k$cluster, tomMtx)
plot(sil)

cass <- data.frame(names=as.character(colnames(l$omtx)),clus=l$km$cluster, stringsAsFactors = F)
write.csv(cass, "~/simsim/data/all_clust_membership.csv")

mtx <- l$omtx
# Look at individual clusters
#banned:  fviz_cluster(km, data=dist(mtx)
set.seed(1337)
facets <- c("abnormality of the endocrine system","abnormality of the cardiovascular system",
            "abnormality of the immune system",
            "abnormality of the musculoskeletal system","abnormality of the genitourinary system",
            "abnormality of metabolism or homeostasis",
            "abnormality of the nervous system","growth abnormality","abnormal cellular phenotype"
            ,"abnormality of blood and blood\\-forming tissues","abnormality of the integument",
            "neoplasm","abnormality of limbs","abnormality of the thoracic cavity",
            "abnormality of the digestive system","abnormality of prenatal development or birth",
            "constitutional symptom","abnormality of head or neck","abnormality of the respiratory system")

memberships <- data.frame(names=as.character(colnames(l$omtx)), stringsAsFactors = F)
csets <- data.frame(facet = character(), centers = double(), silhouette = double(), stringsAsFactors = F)
clusters <- hash()
for(f in facets) {
  print(f)
  fname <- paste("~/simsim/data/facet_matrices/", f, ".mtx", sep = "")
  print(fname)
  l <- processMatrix(fname)
  if(l$bestX != 0) {
    memberships[eval(f)] <- l$km$cluster
  }
  csets <- rbind(csets, data.frame(facet=f, centers=l$bestX, silhouette=l$bestSil))
  clusters[[f]] <- l
}

write.csv(memberships, "~/simsim/data/facet_clust_membership.csv")

plist = list()
for(f in facets) {
  o <-f
  if(!is.null(clusters[[o]]) && clusters[[o]]$bestSil > 0.5) {
    plist = list.append(plist, fviz_cluster(labelsize=0, clusters[[o]]$km,
                                            data=dist(clusters[[o]]$mtx),
                                            main=paste(f, "clusters", sep = " ")) +
                                              theme(legend.key.size = unit(1, 'mm'), #change legend key size
                                                    legend.key.height = unit(1, 'mm'), #change legend key height
                                                    legend.key.width = unit(1, 'mm'), #change legend key width
                                                    legend.title = element_text(size=4), #change legend title font size
                                                    legend.text = element_text(size=4)) #

                        )
  }
}

do.call("grid.arrange", c(plist, ncol = 3))

patcounts = c(524,921,985,681,513,10,962,749,204,546,874,586,407,184,20,829,61,894,436,885)
appcounts = c(0,20,2,0,0,0,4,3,0,5,6,1,0,0,0,7,0,0,0,7)

silcounts = c(0.21,0.84,0.57,0.74,0.39,0.57,-0.23,0.06,0.85,0.81,0.1,0.74,0.73,0.81,-0.02,0.48,0.41)
patcountsnn = c(524,921,985,681,513,962,749,204,546,874,586,407,184,829,61,436,885)
anncountsnn = c(1968,10057,6135,1937,1502,5820,4373,338,1477,5053,1430,1407,209,6448,89,890,7905)

patcounts = c(524,921,985,681,513,10,962,749,204,546,874,586,407,184,20,829,61,894,436,885)
anncounts = c(1968,10057,6135,1937,1502,14,5820,4373,338,1477,5053,1430,1407,209,23,6448,89,4678,890,7905)
appcounts = c(1,7,0,0,0,0,1,3,0,3,1,0,0,0,0,4,0,0,0,1)

cor(anncounts, appcounts, method="pearson")
cor(patcounts, appcounts, method="pearson")
cor(anncountsnn, silcounts, method="pearson")
cor(patcountsnn, silcounts, method="pearson")



patcounts = c(524,921,985,681,513,962,749,204,546,874,586,407,184,20,829,61,894,436,885)
anncounts = c(1968,10057,6135,1937,1502,5820,4373,338,1477,5053,1430,1407,209,23,6448,89,4678,890,7905)
silcount = c(0.7605172,0.2443116,0.2266049,0.4971402,0.6532850,0.2589983,0.4138456,0.8955404,0.7113692,0.4369184,0.6405204,0.6847992,0.9534494,0.9802550,0.4118127,0.9827662,0.3765390,0.7143688,0.2907022)


cor(patcounts, silcount, method="pearson")
cor(anncounts, silcount, method="pearson")

