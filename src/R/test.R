
#install.packages("ggplot2")
#install.packages("reshape2")
#install.packages("gridExtra")
library(ggplot2) 
library(reshape2)
library(gridExtra)

# small dataset
#raw=read.csv("/scratch/testbams/PE100-borrelia-wt.bam.rawcoverage", sep="\t", header=F)
#comp=read.csv("/scratch/testbams/PE100-borrelia-wt.bam.compressed", sep="\t", header=F)
#decomp=read.csv("/scratch/testbams/PE100-borrelia-wt.bam.decompressed", sep="\t", header=F)
#
## show how the method works
#plot(comp$V2, comp$V3, ylim=c(0, max(comp$V3,raw$V3,decomp$V3)), xlab="pos", ylab="coverage")
#lines(raw$V2, raw$V3,col='blue')
#lines(decomp$V2, decomp$V3,col='red')
#
## zoomed in
#plot(comp$V2, comp$V3, ylim=c(0, 100), xlab="pos", ylab="coverage")
#lines(raw$V2, raw$V3,col='blue')
#lines(decomp$V2, decomp$V3,col='red')
#
##-----------------------------------------------
#
## stats large dataset
#rawhead=read.csv("/scratch/testbams/NA12878.HiSeq.WGS.bwa.cleaned.recal.hg19.20.bam.rawcoverage.head", sep="\t", header=F)
#decomphead=read.csv("/scratch/testbams/NA12878.HiSeq.WGS.bwa.cleaned.recal.hg19.20.bam.interpolated.head", sep="\t", header=F)
#comp=read.csv("/scratch/testbams/NA12878.HiSeq.WGS.bwa.cleaned.recal.hg19.20.bam.compressed", sep="\t", header=F,comment.char = "#",)
## calc x[i] - x[i-1]
#diffencodings=diff(comp$V2)
#qplot( diffencodings, y = ..scaled.., geom="density", log="x" )

##-----------------------------------------------
#
## show stats
#
#stats=read.csv("/scratch/testbams/NA12878.HiSeq.WGS.bwa.cleaned.recal.hg19.20.bam.stats", sep="\t", header=T,comment.char = "#",)
#
#
#par(mar=c(5,4,4,5)+.1)
#plot(stats$bucket.max, stats$meandiff, xlab="Coverage bucket", ylab="mean coverage difference")
#par(new=TRUE)
#plot(stats$bucket.max, 100*stats$entries/sum(stats$entries), type="l", col='blue',xaxt="n",yaxt="n",xlab="",ylab="")
#axis(4)
#mtext("entries [%]",side=4,line=3)
#legend("topleft",col=c("black","blue"),lty=1,legend=c("cov diff","entries [%]"))

#-----------------------------------------------
#stats=read.csv("/project/oesi/bgraph/compression-eval/results-small/small.bam.DIFFREPORT.csv", sep="\t", header=T,comment.char = "#",)
#sizes=read.csv("/project/oesi/bgraph/compression-eval/results-small/small.bam.SIZEREPORT.csv", sep="\t", header=T,comment.char = "#",)

stats=read.csv("/project/oesi/bgraph/compression-eval/results-borrelia-13524/13524_ATCACG_D223KACXX_3_20130430B_20130430_1.trimmed.bam.DIFFREPORT.csv", sep="\t", header=T,comment.char = "#",)
sizes=read.csv("/project/oesi/bgraph/compression-eval/results-borrelia-13524/13524_ATCACG_D223KACXX_3_20130430B_20130430_1.trimmed.bam.SIZEREPORT.csv", sep="\t", header=T,comment.char = "#",)

#stats=read.csv("/project/oesi/bgraph/compression-eval/results-human-NA12878/NA12878.HiSeq.WGS.bwa.cleaned.recal.hg19.20-nounmapped.bam.DIFFREPORT.csv", sep="\t", header=T,comment.char = "#",)
#sizes=read.csv("/project/oesi/bgraph/compression-eval/results-human-NA12878/NA12878.HiSeq.WGS.bwa.cleaned.recal.hg19.20-nounmapped.bam.SIZEREPORT.csv", sep="\t", header=T,comment.char = "#",)

shapes = c(	0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)		
cols = rainbow(11, start=0.3, end=1)		
buckets=c("0-0","0-1","1-2","2-4","4-8","8-16","16-32","32-64","64-128","128-256","256-512","512-1024")
linetypes=c("solid", "solid","solid","solid","solid","dashed","dashed","dashed","dashed","dashed","dashed","dashed")

# plot
g0=ggplot(data=data.frame(sizes$size.gziped.byte, sizes$meandiff), 
		aes(x=sizes$size.gziped.byte, y=sizes$meandif, label=sizes$name, color=sizes$name )) + 
		geom_point(aes(shape=shapes, color=sizes$name)) + 
		scale_color_manual(values=cols) +
		scale_shape_identity() +
		xlab("GZiped Size [byte]") + 
		ylab("Mean Difference") +
		theme(legend.position = "none") +
		geom_text(size=3, hjust = 0.5, vjust=-0.7)

g1=ggplot(data = data.frame(stats$bucket, stats$count), 
	aes(x=stats$bucket, y=stats$count, color=stats$name, linetype=stats$name, label=stats$name)) + 
	geom_line() + 
	scale_x_discrete(labels=buckets) + 
	scale_linetype_manual(values=linetypes) +
	scale_color_manual(values=cols) +
	xlab("Bucket") + 
	ylab("Counts") +
	theme(legend.position = "none")

g2=ggplot(data = data.frame(stats$bucket, stats$meandiff), 
		aes(x=stats$bucket, y=stats$meandiff, linetype = stats$name, color=stats$name, label=stats$name)) + 
		geom_line() + 
		scale_shape_identity() +
		scale_x_discrete(labels=buckets) + 
		scale_linetype_manual(values=linetypes) +
		scale_y_log10() +
		scale_color_manual(values=cols) +
		xlab("Bucket") + 
		ylab("Mean difference") +
		theme(legend.position = "right", legend.title=element_blank())

plotall = grid.arrange(g0,arrangeGrob(g1,g2,ncol=2,nrow=1),nrow=2)



# check coverage
#realcov=read.csv("/scratch/testbams/evaluation2/13524_ATCACG_D223KACXX_3_20130430B_20130430_1.trimmed.bam-WRG.bam.helper.rawcoverage", sep="\t", header=T,comment.char = "#",)

