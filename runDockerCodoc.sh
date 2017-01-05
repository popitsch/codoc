IN=$1
OUT=$2

# run codoc
sudo docker run -t -i --name codoc -v $(pwd)/${IN}:/data.bam npopitsch/codoc:0.0.2 compress -cov /data.bam -o /data.codoc
sudo docker cp codoc:/data.codoc $(pwd)/${OUT}
sudo docker rm codoc 
