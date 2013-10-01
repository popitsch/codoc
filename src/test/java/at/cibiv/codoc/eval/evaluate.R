
#install.packages("ggplot2")
#install.packages("reshape2")
#install.packages("gridExtra") 
#install.packages("directlabels")
#install.packages("cwhmisc")

library(ggplot2) 
library(reshape2)
library(gridExtra)
library(directlabels)
library(cwhmisc)

#PREFIX="/project/oesi/bgraph/compression-eval/data/sakai_mis_2x250pb-both-trimmed/sakai_mis_2x250pb-both-trimmed.bam.";
#PREFIX="/project/oesi/bgraph/compression-eval/data/sakai_mis_2x150pb-both-trimmed/sakai_mis_2x150pb-both-trimmed.bam.";
#PREFIX="/project/oesi/bgraph/compression-eval/data/sakai_pgm_400bp/sakai_pgm_400bp.bam.";
#PREFIX="/project/oesi/bgraph/compression-eval/data/xie_UCSC_Genome/xie_UCSC_Genome-sorted.bam.";
#PREFIX="/project/oesi/bgraph/compression-eval/data/geuvadis_UCSC_Genome/geuvadis_UCSC_Genome-sorted.bam.";
PREFIX="/project/oesi/bgraph/compression-eval/data/NA12878/NA12878.HiSeq.WGS.bwa.cleaned.recal.hg19.20-nounmapped-FILTERED.bam.";

fileGraphOutput=paste(PREFIX,"diffPerBin.pdf",sep='')
filename=substring(PREFIX, substring.location(PREFIX, "/")$last[length(substring.location(PREFIX, "/")$last)]+1)

data_head_p0=read.csv(paste(PREFIX,"p0.comp.RESULTS.head.csv",sep=''),sep='\t',header=F)
data_head_g2=read.csv(paste(PREFIX,"g2.comp.RESULTS.head.csv",sep=''),sep='\t',header=F)
data_head_g5=read.csv(paste(PREFIX,"g5.comp.RESULTS.head.csv",sep=''),sep='\t',header=F)
data_head_l2=read.csv(paste(PREFIX,"l2.comp.RESULTS.head.csv",sep=''),sep='\t',header=F)
data_head_p001=read.csv(paste(PREFIX,"p001.comp.RESULTS.head.csv",sep=''),sep='\t',header=F)
data_head_p005=read.csv(paste(PREFIX,"p005.comp.RESULTS.head.csv",sep=''),sep='\t',header=F)
data_head_p01=read.csv(paste(PREFIX,"p01.comp.RESULTS.head.csv",sep=''),sep='\t',header=F)
data_head_p02=read.csv(paste(PREFIX,"p02.comp.RESULTS.head.csv",sep=''),sep='\t',header=F)
data_head_w1=read.csv(paste(PREFIX,"w1.tdf.RESULTS.head.csv",sep=''),sep='\t',header=F)
data_head_w5=read.csv(paste(PREFIX,"w5.tdf.RESULTS.head.csv",sep=''),sep='\t',header=F)
data_head_w10=read.csv(paste(PREFIX,"w10.tdf.RESULTS.head.csv",sep=''),sep='\t',header=F)
data_head_w50=read.csv(paste(PREFIX,"w50.tdf.RESULTS.head.csv",sep=''),sep='\t',header=F)
data_head_w100=read.csv(paste(PREFIX,"w100.tdf.RESULTS.head.csv",sep=''),sep='\t',header=F)

data_tail_p0=read.csv(paste(PREFIX,"p0.comp.RESULTS.tail.csv",sep=''),sep='\t')
data_tail_g2=read.csv(paste(PREFIX,"g2.comp.RESULTS.tail.csv",sep=''),sep='\t')
data_tail_g5=read.csv(paste(PREFIX,"g5.comp.RESULTS.tail.csv",sep=''),sep='\t')
data_tail_l2=read.csv(paste(PREFIX,"l2.comp.RESULTS.tail.csv",sep=''),sep='\t')
data_tail_p001=read.csv(paste(PREFIX,"p001.comp.RESULTS.tail.csv",sep=''),sep='\t')
data_tail_p005=read.csv(paste(PREFIX,"p005.comp.RESULTS.tail.csv",sep=''),sep='\t')
data_tail_p01=read.csv(paste(PREFIX,"p01.comp.RESULTS.tail.csv",sep=''),sep='\t')
data_tail_p02=read.csv(paste(PREFIX,"p02.comp.RESULTS.tail.csv",sep=''),sep='\t')
data_tail_w1=read.csv(paste(PREFIX,"w1.tdf.RESULTS.tail.csv",sep=''),sep='\t')
data_tail_w5=read.csv(paste(PREFIX,"w5.tdf.RESULTS.tail.csv",sep=''),sep='\t')
data_tail_w10=read.csv(paste(PREFIX,"w10.tdf.RESULTS.tail.csv",sep=''),sep='\t')
data_tail_w50=read.csv(paste(PREFIX,"w50.tdf.RESULTS.tail.csv",sep=''),sep='\t')
data_tail_w100=read.csv(paste(PREFIX,"w100.tdf.RESULTS.tail.csv",sep=''),sep='\t')


meandiffs=data.frame(data_tail_g2$bucket.border, 
		data_tail_p0$meandiff, 
		data_tail_g2$meandiff, 
		data_tail_g5$meandiff, 
		data_tail_l2$meandiff,
		data_tail_p001$meandiff,
		data_tail_p005$meandiff,
		data_tail_p01$meandiff,
		data_tail_p02$meandiff,
		data_tail_w1$meandiff,
		data_tail_w5$meandiff,
		data_tail_w10$meandiff,
		data_tail_w50$meandiff,
		data_tail_w100$meandiff)
colnames(meandiffs)=c("bucket", "p0", "g2", "g5", "log2", "p001", "p005", "p01", "p02", "w1", "w5", "w10", "w50", "w100")

shapes = seq(0,12)
cols = rainbow(13, start=0.3, end=1)	
buckets=c("[0]","[1]","[1,2]","[2,4]","[4,8]","[8,16]","[16,32]","[32,64]","[64,128]","[128,256]","[256,512]","[512,1024]")

#linetypes=c(rep("solid",12), rep("solid",12),rep("solid",12),rep("solid",12),rep("solid",12),rep("solid",12),rep("solid",12),
#		rep("dashed",12),rep("dashed",12),rep("dashed",12),rep("dashed",12),rep("dashed",12))
#
#meandiffs_plotable=cbind( meandiffs_plotable, shapes )
#meandiffs_plotable=cbind( meandiffs_plotable, cols )
#meandiffs_plotable=cbind( meandiffs_plotable, linetypes)
#
#g0=ggplot(data = meandiffs_plotable, 
#		aes(x=bucket, value)) +
#		guides(shape=guide_legend(override.aes=list(size=15))) +
#		scale_shape_identity() +
#		geom_line(aes(color=cols, linetype=linetypes)) + 
#		geom_point(aes(color=cols, shape=shapes)) + 
#		#scale_linetype_manual(values=linetypes) + 
#		xlab("Bucket") + 
#		ylab("Average difference") +
#		#theme(legend.position = "none") + 
#		#theme(legend.position = "right", legend.title=element_blank())
#		scale_x_log10(breaks=meandiffs$bucket, labels=buckets )

labelx=1300
labelfontsize=4

mybreaks=c(0.7,1,2,4,8,16,32,64,128,256,512,1024 )

g0=ggplot( meandiffs, aes(bucket)) + 

	geom_line(linetype="solid", aes(y=p0, color=cols[1], label="A") ) +
	geom_point(shape=shapes[1], aes(y=p0, color=cols[1]) ) +
	annotate("text", label = "p0", x = labelx, y = meandiffs$p0[12], size=labelfontsize) +
		
	geom_line(linetype="solid", aes(y=g2, color=cols[2], label="A") ) +
	geom_point(shape=shapes[2], aes(y=g2, color=cols[2]) ) +
	annotate("text", label = "g2", x = labelx, y = meandiffs$g2[12], size=labelfontsize) +
	
	geom_line(linetype="solid", aes(y=g5, color=cols[3]) ) +
	geom_point(shape=shapes[3], aes(y=g5, color=cols[3]) ) +
	annotate("text", label = "g5", x = labelx, y = meandiffs$g5[12], size=labelfontsize) +

	geom_line(linetype="solid", aes(y=log2, color=cols[4]) ) +
	geom_point(shape=shapes[4], aes(y=log2, color=cols[4]) ) +
	annotate("text", label = "log2", x = labelx, y = meandiffs$log2[12], size=labelfontsize, vjust=.5) +
	
	geom_line(linetype="solid", aes(y=p001, color=cols[5]) ) +
	geom_point(shape=shapes[5], aes(y=p001, color=cols[5]) ) +
	annotate("text", label = "p001", x = labelx, y = meandiffs$p001[12], size=labelfontsize) +
	
	geom_line(linetype="solid", aes(y=p005, color=cols[6]) ) +
	geom_point(shape=shapes[6], aes(y=p005, color=cols[6]) ) +
	annotate("text", label = "p005", x = labelx, y = meandiffs$p005[12], size=labelfontsize) +
	
	geom_line(linetype="solid", aes(y=p01, color=cols[7]) ) +
	geom_point(shape=shapes[7], aes(y=p01, color=cols[7]) ) +
	annotate("text", label = "p01", x = labelx, y = meandiffs$p01[12], size=labelfontsize) +
	
	geom_line(linetype="solid", aes(y=p02, color=cols[8]) ) +
	geom_point(shape=shapes[8], aes(y=p02, color=cols[8]) ) +
	annotate("text", label = "p02", x = labelx, y = meandiffs$p02[12], size=labelfontsize) +
	
	geom_line(linetype="dashed", aes(y=w1, color = cols[9]) ) +
	geom_point(shape=shapes[9], aes(y=w1, color=cols[9]) ) +
	annotate("text", label = "w1", x = labelx, y = (meandiffs$w1[12]+0.0000001), size=labelfontsize) +
	
	geom_line(linetype="dashed", aes(y=w5, color = cols[10]) ) +
	geom_point(shape=shapes[10], aes(y=w5, color=cols[10]) ) +
	annotate("text", label = "w5", x = labelx, y = meandiffs$w5[12], size=labelfontsize) +
	
	geom_line(linetype="dashed", aes(y=w10, color = cols[11]) ) +
	geom_point(shape=shapes[11], aes(y=w10, color=cols[11]) ) +
	annotate("text", label = "w10", x = labelx, y = meandiffs$w10[12], size=labelfontsize) +
	
	geom_line(linetype="dashed", aes(y=w50, color = cols[12]) ) +
	geom_point(shape=shapes[12], aes(y=w50, color=cols[12]) ) +
	annotate("text", label = "w50", x = labelx, y = meandiffs$w50[12], size=labelfontsize) +
	
	geom_line(linetype="dashed", aes(y=w100, color = cols[13]) ) +
	geom_point(shape=shapes[13], aes(y=w100, color=cols[13]) ) +
	annotate("text", label = "w100", x = labelx, y = meandiffs$w100[12], size=labelfontsize) +
	
	xlab("Coverage Segment") + 
	ylab("Average difference from true signal per bucket [log]") +	
	scale_y_log10() +
	scale_x_log10(breaks=mybreaks, labels=buckets ) + 

	theme(legend.position = "none") +
	
	ggtitle(paste("Lossy compression error\n", filename,sep='')) + 
	theme(plot.title = element_text(lineheight=.8, face="bold"))
		
	   



sizes=as.numeric(c(
		as.vector(data_head_p0$V2)[4], 
		as.vector(data_head_g2$V2)[4], 
		as.vector(data_head_g5$V2)[4], 
		as.vector(data_head_l2$V2)[4],
		as.vector(data_head_p001$V2)[4],
		as.vector(data_head_p005$V2)[4],
		as.vector(data_head_p01$V2)[4],
		as.vector(data_head_p02$V2)[4],
		as.vector(data_head_w1$V2)[4],
		as.vector(data_head_w5$V2)[4],
		as.vector(data_head_w10$V2)[4],
		as.vector(data_head_w50$V2)[4],
		as.vector(data_head_w100$V2)[4]))

avediffs=c(
		ave(data_tail_p0$meandiff)[1],
		ave(data_tail_g2$meandiff)[1],
		ave(data_tail_g5$meandiff)[1],
		ave(data_tail_l2$meandiff)[1],
		ave(data_tail_p001$meandiff)[1],
		ave(data_tail_p005$meandiff)[1],
		ave(data_tail_p01$meandiff)[1],
		ave(data_tail_p02$meandiff)[1],
		ave(data_tail_w1$meandiff)[1],
		ave(data_tail_w5$meandiff)[1],
		ave(data_tail_w10$meandiff)[1],
		ave(data_tail_w50$meandiff)[1],
		ave(data_tail_w100$meandiff)[1])
labels=c( "p0", "g2", "g5", "log2", "p001", "p005", "p01", "p02", "w1", "w5", "w10", "w50", "w100")

data=data.frame(sizes,avediffs,labels,cols,shapes)

g1=ggplot( data, aes(sizes, avediffs, label=labels, color=cols)) + 
		geom_point(shape=shapes,  ) +
		scale_x_log10() +
		ggtitle("Average lossy compression error vs. compressed size") + 
		xlab("File size [byte]") + 
		ylab("Average difference from true signal") +	
		theme(plot.title = element_text(lineheight=.8, face="bold")) +
		theme(legend.position = "none")

for ( i in 1:length(sizes) )
	g1=g1+annotate("text", x = (sizes[i] + 1000), y = (avediffs[i]+0.5), label = labels[i], size=labelfontsize)

# Prepare the output files
pdf(fileGraphOutput, width=13,height=12)
par(mfrow=c(1,1))

grid.arrange(g0, g1, nrow=2)

dev.off()
grid.arrange(g0, g1, nrow=2)
