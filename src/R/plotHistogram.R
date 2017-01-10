# prints a histogram from a CODOC file
# FIXME: works only if all bins are there! This is currently ensured by the extractHist() method in CoverageDecompressor.
library(jsonlite)
args <- commandArgs(trailingOnly = TRUE)
args=c("c:/data/tmp/tdf_StA_BS/tdf_StA_ES.codoc.hist", "c:/data/tmp/tdf_StA_BS/tdf_StA_ES.codoc.pdf")

print(args)
if (length(args) < 2) {
  stop("usage: Rscript plotHistogram.R <input-json> <output-pdf> [<max-bins> <chrsizes>]
			Use max-bins=0 to print all bins. Default is 100.
			Use <chrsizes> to pass total width of covered regions for autosomes, x, y. Should correspond to the ROIs that were used with CODOC.
			Example: Rscript plotHistogram.R A.hist A.hist.pdf 100 2684578479,151100560,25653566")
			
}
# sizes for hg19 including gaps
#autosize=2881033286
#xsize=155270560
#ysize=59373566

# sizes for hg19 w/o gaps
autosize=2684578479
xsize=151100560
ysize=25653566

f=args[1]
out=args[2]
max=100
normmult=100
if ( length(args)>=3) {
  max=as.numeric(args[3])
  if ( max == 0)
    max = 100
  tmp=unlist(strsplit(args[4],","))
  autosize=as.numeric(tmp[1])
  xsize=as.numeric(tmp[2])
  ysize=as.numeric(tmp[3])
  }

d=fromJSON(f)

print(paste0("Used normalization parameters (auto/x/y covered regions size): auto=", autosize,", x=", xsize, ", y=", ysize))


pdf(out, width=14, height=8)


for ( region in c("Autosomal", "X-Chromosome", "Y-Chromosome")) {
  hist=NA
  med=NA
  if ( region == "Autosomal") {
    hist = d$`histogram-autosomes`
    med  = d$`median-autosomes`
  }
  if ( region == "X-Chromosome") {
    hist = d$`histogram-X`
    med  = d$`median-X`
  }
  if ( region == "Y-Chromosome") {
    hist = d$`histogram-Y`
    med  = d$`median-Y`
  }
  
  bins=hist$bins
  counts=hist$counts
  
  title=paste(region, "histogram for ", basename(f))
  if (!is.na(max) ) {
    bins=head(bins, n=max)  
    counts=head(counts, n=max)  
    title=paste0(title, ", only first ", max, " bins shown")
  } 
  
  bp=barplot(counts, axes=F, space=0, border = NA)
  maxshownbins=25
  if ( length(bins) > maxshownbins ) {
    axis(1, at=bp[seq(1, length(bins), round(length(bins)/maxshownbins) )], labels=bins[seq(1, length(bins), round(length(bins)/maxshownbins) )], cex.axis=0.7, las=2)
  } else {
    axis(1, at=bp, labels=bins, cex.axis=0.7, las=2)
  }
  axis(2, cex.axis=0.8)
  # draw median and account for zero-bin
  abline(v=med+0.5, col="red", lty=2)
  text(med+1, max(counts), paste("median =", med), col="red", cex=0.7, adj=c(-0.1,1.1))
  title(title, xlab="coverage", ylab="counts")
 
  xmin = par("usr")[1]
  xmax = par("usr")[2]
  ymin = par("usr")[3]
  ymax = par("usr")[4] 
  lgd=legend(x = mean(c(xmin,xmax)), y =  mean(c(ymin,ymax)), c("Coverage Histogram by CODOC"), plot=F)
  legend(x = xmax - lgd$rect$w, y =  ymin + lgd$rect$h, c("Coverage Histogram by CODOC"), plot=T, bty = "n", cex=0.5)
}

# overlay
p=NA
ylim=max(
  d$`histogram-autosomes`$counts[d$`median-autosomes`+1]*normmult/autosize, 
  d$`histogram-X`$counts[d$`median-X`+1]*normmult/xsize,  
  d$`histogram-Y`$counts[d$`median-Y`+1]*normmult/ysize)
leg=c()
legc=c()

for ( region in c("Autosomal", "X-Chromosome", "Y-Chromosome")) {
  hist=NA
  med=NA
  size=NA
  if ( region == "Autosomal") {
    hist = d$`histogram-autosomes`
    med  = d$`median-autosomes`
    col="black"
    lty=1
    size=autosize
    leg=c(leg, paste0("Autosomes (median:", med,")"))
    legc=c(legc,col)  
  }
  if ( region == "X-Chromosome") {
    hist = d$`histogram-X`
    med  = d$`median-X`
    col="magenta"
    lty=2
    size=xsize
    leg=c(leg, paste0("X-chrom (median:", med,")"))
    legc=c(legc,col)  
  }
  if ( region == "Y-Chromosome") {
    hist = d$`histogram-Y`
    med  = d$`median-Y`
    col="blue"
    lty=3
    size=ysize
    leg=c(leg, paste0("Y-chrom (median:", med,")"))
    legc=c(legc,col)  
  }
  title=paste("Overlay histogram for ", basename(f))
  bins=hist$bins
  counts=hist$counts*normmult/size
  if (!is.na(max) ) {
    bins=head(bins, n=max)  
    counts=head(counts, n=max)  
    title=paste0(title, ", only first ", max, " bins shown")
  } 
  title=paste0(title, "\nCounts are normalized by chrom size and multiplied by ", normmult)
 
   if ( is.na(p)) {
    plot(counts, type="l", col=col, xlim=c(0,max), ylim=c(0,ylim), xlab="coverage", ylab=paste0("counts*", normmult,"/size"), main=title)
    p="OK"
  } else {
    lines(counts, col=col)
  }
  abline(v=med, col=col, lty=lty)
  #text(med+1, max(counts), paste("median =", med), col=col, cex=0.7, adj=c(-0.1,1.1))
}
legend("topright", leg, fill=legc)
xmin = par("usr")[1]
xmax = par("usr")[2]
ymin = par("usr")[3]
ymax = par("usr")[4] 
lgd=legend(x = mean(c(xmin,xmax)), y =  mean(c(ymin,ymax)), c("Coverage Histogram by CODOC"), plot=F)
legend(x = xmax - lgd$rect$w, y =  ymin + lgd$rect$h, c("Coverage Histogram by CODOC"), plot=T, bty = "n", cex=0.5)


dev.off()
