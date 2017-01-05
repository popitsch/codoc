# prints a histogram from a CODOC file
# FIXME: works only if all bins are there! This is currently ensured by the extractHist() method in CoverageDecompressor.
library(jsonlite)
args <- commandArgs(trailingOnly = TRUE)
print(args)
if (length(args) < 2) {
  stop("usage: Rscript plotCohortHistogram.R <input-file-list> <output-pdf> [<max-bins>] [ylim]
       Use max-bins=0 to print all bins. Default is 100.
       The ylim of the plot can be set manually or will otherwise be set to the max of all coverage histograms at their median
       Example: Rscript plotCohortHistogram.R A.hist A.hist.pdf 300 150000")
  
}
filelist=args[1]
out=args[2]
maxbins=100
ylim=NA
if ( length(args)>=3) {
  maxbins=as.numeric(args[3])
  if ( maxbins == 0)
    maxbins = 100
  if ( length(args)>=4) {
    ylim=as.numeric(args[4])
  }
}


#filelist="/temp/Niko/ClinEx/test"


pdf(out, width=14, height=8)

title=paste("Autosomal coverage for", basename(filelist))
if (!is.na(maxbins) ) {
  maxbins=head(maxbins, n=maxbins)  
  title=paste0(title, ", only first ", maxbins, " bins shown")
} 

fl=read.table(filelist, stringsAsFactors = F)
# estimate maximum
if ( is.na(ylim) ) {
  ylim=0
  for ( f in fl$V1) {
    d=fromJSON(f)
    med  = d$`median-autosomes`
    ylim=max(ylim, d$`histogram-autosomes`$counts[med])
  }
}

plot(1, type="n", xlim=c(0, maxbins), ylim=c(0, ylim), xlab="coverage", ylab="counts", main=title)
col=rainbow(n=length(fl$V1))

for ( i in seq(1,length(fl$V1))) {
  f=fl$V1[i]
  d=fromJSON(f)
  hist = d$`histogram-autosomes`
  med  = d$`median-autosomes`
  bins=hist$bins
  counts=hist$counts
  lines(bins, counts, col=col[i])
}

xmin = par("usr")[1]
xmax = par("usr")[2]
ymin = par("usr")[3]
ymax = par("usr")[4] 
lgd=legend(x = mean(c(xmin,xmax)), y =  mean(c(ymin,ymax)), c("Coverage Histogram by CODOC"), plot=F)
legend(x = xmax - lgd$rect$w, y =  ymin + lgd$rect$h, c("Coverage Histogram by CODOC"), plot=T, bty = "n", cex=0.5)

if ( length(fl$V1) > 10 ) {
  plot(1, type="n", xaxt='n', yaxt='n', xlab="", ylab="")
}
legend("topright", fill=col, legend=basename(fl$V1), ncol = 4, cex = 0.8)


dev.off()
