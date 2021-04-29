library(similecari)
library(readr)
library(dynamicTreeCut)
library(cluster)
library(factoextra)

mtx <- read.csv("~/simsim/data/facet_matrices/all_score.lst", row.names = 1, sep = ",", header = TRUE)
mtx <- data.matrix(mtx)

# what the f****
colnames(mtx) <- gsub(x = colnames(mtx), pattern = "\\.", replacement = ":")
colnames(mtx) <- gsub(x = colnames(mtx), pattern = "X", replacement = "")

tomMtx <- calculateDistanceMatrix(mtx)

clust <- hclust(as.dist(tomMtx), method = "min")

sizeGrWindow(12,9)
plot(clust, xlab="", sub="",
     main = "Subject clustering on TOM-based dissimilarity",
     labels = FALSE, hang = 0.04)
# set a minimum subject threshold

set.seed(1337)
k <- kmeans(as.dist(tomMtx), centers=7)
fviz_cluster(k, data = tomMtx)
sil <- silhouette(k$cluster, tomMtx)
plot(sil)

cass <- data.frame(names=as.character(colnames(mtx)),clus=k$cluster)
cass$names <- as.character(cass$names) # idk why the heckity heck it doesn't work above, but ok

minModuleSize = 10;
# Module identification using dynamic tree cut:
dynamicMods = cutreeDynamic(dendro = clust, distM = tomMtx,
                            deepSplit = 2, pamRespectsDendro = FALSE,
                            minClusterSize = minModuleSize);

dynamicMods <- cutree(clust, tomMtx, k=10)

# view number of clusters and amount of membership
table(dynamicMods)

sil <- silhouette(dynamicMods, tomMtx)
plot(sil)

dynamicColors = WGCNA::labels2colors(dynamicMods)
table(dynamicColors)

myMods <- as.data.frame(matrix(NA, ncol = 2, nrow = length(dynamicColors)))
colnames(myMods) <- c("Color","Module")
myMods$Color <- dynamicColors
myMods$Module <- dynamicMods


sizeGrWindow(8,6)
WGCNA::plotDendroAndColors(clust, dynamicColors, "Dynamic Tree Cut",
                    dendroLabels = F, hang = 0.03,
                    addGuide = TRUE, guideHang = 0.05,
                    main = "Subject dendrogram and module colors")
