rm users.txt
touch users.txt

for i in {1..2500000}
do
	echo -n \"$i\":\" >> users.txt
	echo -n $i | sha256sum | head -c 64 >> users.txt
	echo \" >> users.txt
done
