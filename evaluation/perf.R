library(readr)
library(hash)

rangey <- function(x){(x-min(x))/(max(x)-min(x))}
getAUC <- function(df, scoreCol) {
  scoreDf <- df[, c(eval(scoreCol), "match")]
  scoreDf$score <- rangey(scoreDf[[scoreCol]])
  scoreDf$match <- as.factor(scoreDf$match)

  result <- hash()
  result[["pr"]] <- pROC::roc(scoreDf, "match", "score", ci=TRUE)
  result[["aur"]] < AUC::roc(scoreDf$score, scoreDf$match)

  return(result)
}

# Load file
facet_matrix <- read_csv("~/simsim/data/facet_matrix.lst")
facets <- c("abnormality of the endocrine system","abnormality of the cardiovascular system",
            "abnormality of the immune system",
            "abnormality of the musculoskeletal system","abnormality of the genitourinary system",
            "abnormality of the voice","abnormality of metabolism/homeostasis",
            "abnormality of the nervous system","growth abnormality","abnormal cellular phenotype"
            ,"abnormality of blood and blood\\-forming tissues","abnormality of the integument",
            "neoplasm","abnormality of limbs","abnormality of the thoracic cavity",
            "abnormality of the digestive system","abnormality of prenatal development or birth",
            "constitutional symptom","abnormality of head or neck","abnormality of the respiratory system")

for(y in facets) {
  facet_matrix[[y]] <- rangey(facet_matrix[[y]])
}

# 1. Check performance of all

allResult <- getAUC(facet_matrix, "all_score")
allResult[["pr"]]

paResult <- getAUC(facet_matrix, "phenotypic abnormality")
paResult[["pr"]]

# 2. Check performance of sum

facet_matrix$sum_score <- apply(facet_matrix, 1, function(r) {
  sum <- 0
  for(n in facets) {
    sum = sum + as.numeric(r[[n]])
  }
  return(sum)
})

sumResult <- getAUC(facet_matrix, "sum_score")
sumResult[["pr"]]

# 3. Check performance of mean

facet_matrix$mean_score <- apply(facet_matrix, 1, function(r) {
  return(as.numeric(r[["sum_score"]]) / length(facets))
})

meanResult <- getAUC(facet_matrix, "mean_score")
meanResult[["pr"]]

# 4. Check performance of max

facet_matrix$max_score <- apply(facet_matrix, 1, function(r) {
  max <- 0
  for(n in facets) {
    v <- as.numeric(r[[n]])
    if(v > max) {
      max <- v
    }
  }
  return(max)
})

maxResult <- getAUC(facet_matrix, "max_score")
maxResult[["pr"]]

# 4. Check significantly different distributions

