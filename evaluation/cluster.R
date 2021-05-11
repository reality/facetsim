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
  for(x in 2:20) {
    gc()
    km <- kmeans(tomMtx, centers=x)
    ss <- silhouette(km$cluster, as.dist(tomMtx), iter.max=20000)
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
    "mtx" = tomMtx
  ))
  #print(bestSil)
  #print(bestX)
  #fviz_cluster(bestModel, data = tomMtx)
}

l <- processMatrix(paste("~/simsim/data/facet_matrices/abnormality of limbs.mtx", sep = ""))

# Look at all
set.seed(1337)
k <- kmeans(as.dist(tomMtx), centers=7)
fviz_cluster(k, data = tomMtx)
sil <- silhouette(k$cluster, tomMtx)
plot(sil)

cass <- data.frame(names=as.character(colnames(mtx)),clus=k$cluster)
cass$names <- as.character(cass$names) # idk why the heckity heck it doesn't work above, but ok
write.csv(cass, "~/simsim/data/all_clust_membership.csv")

# Look at individual clusters
set.seed(1337)
facets <- c("abnormality of the endocrine system","abnormality of the cardiovascular system",
            "abnormality of the immune system",
            "abnormality of the musculoskeletal system","abnormality of the genitourinary system",
            "abnormality of the voice","abnormality of metabolism or homeostasis",
            "abnormality of the nervous system","growth abnormality","abnormal cellular phenotype"
            ,"abnormality of blood and blood\\-forming tissues","abnormality of the integument",
            "neoplasm","abnormality of limbs","abnormality of the thoracic cavity",
            "abnormality of the digestive system","abnormality of prenatal development or birth",
            "constitutional symptom","abnormality of head or neck","abnormality of the respiratory system")

memberships <- data.frame(names=as.character(colnames(mtx)), stringsAsFactors = F)
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
}

write.csv(memberships, "~/simsim/data/facet_clust_membership.csv")

plist = list()
for(f in facets) {
  if(clusters[[f]]$bestSil > 0.5) {
    plist = list.append(plist, fviz_cluster(clusters[[f]]$km, data=clusters[[f]]$mtx, main=paste(f, "clusters", sep = " ")))
  }
}

do.call("grid.arrange", c(plist, ncol = 3))

patcounts = c(524,921,985,681,513,10,962,749,204,546,874,586,407,184,20,829,61,894,436,885)
appcounts = c(0,20,2,0,0,0,4,3,0,5,6,1,0,0,0,7,0,0,0,7)

silcounts = c(0.21,0.84,0.57,0.74,0.39,0.57,-0.23,0.06,0.85,0.81,0.1,0.74,0.73,0.81,-0.02,0.48,0.41)
patcountsnn = c(524,921,985,681,513,962,749,204,546,874,586,407,184,829,61,436,885)
anncountsnn = c(1968,10057,6135,1937,1502,5820,4373,338,1477,5053,1430,1407,209,6448,89,890,7905)

cor(anncounts, appcounts, method="pearson")
cor(patcounts, appcounts, method="pearson")
cor(anncountsnn, silcounts, method="pearson")
cor(patcountsnn, silcounts, method="pearson")

