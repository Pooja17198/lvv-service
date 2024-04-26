## Accessing Kubernetes Cluster Resources

### Download the kubectl tooling and create your Kube Config

[Kubernetes Documentation](https://kubernetes.io/docs/home/)

Client Downloads

    brew install kubectl
    curl -LO https://github.com/kvaps/kubectl-node-shell/raw/master/kubectl-node_shell
    chmod +x ./kubectl-node_shell
    sudo mv ./kubectl-node_shell /usr/local/bin/kubectl-node_shell

Authenticate kubectl with oci cli and create config

    mkdir -p $HOME/.kube
    oci ce cluster create-kubeconfig --cluster-id <cluster_ocid> --file $HOME/.kube/config --region us-phoenix-1 --token-version 2.0.0
    □    Cluster ocid can be grabbed from the console under the respective compartment
    □    Will have to run this again if oci cli needed to authenticate using ocna-saml the first time
    export KUBECONFIG=$HOME/.kube/config


## Describing OKE Resources

Test kubectl connection to your kube config
	
    kubectl version
    kubectl get nodes

Checking cluster details

	kubectl cluster-info
    kubectl describe node

Get app/service/deployment info

	kubectl get deployments
	kubectl get pods -l app=<app_name> -o wide
	kubectl describe deployment <deployment_name>
	kubectl get service <service_name>

Debug host session

	kubectl get pods
	kubectl debug <pod_name> -it --image=busybox
	□	Creates an interactive host session on a public, managed pod

Deploy a "helm chart"

	kubectl apply -f <yaml file>
    □	A helm chart is effectively a deployment yaml that describes components and metadata describing them using the kubectl client

Delete previously deployed app/service

	kubectl delete -f <same yaml file>

Verify deployment of a service

	kubectl get services
	□	For public, should be an externally-exposed IP in there
	□	This is the IP you'd use in the browser for, say, the UI


## Using kubectl node shell to create an SSH session

Start a root shell in the node's host OS running

https://github.com/kvaps/kubectl-node-shell

	# Get standard bash shell
	kubectl node-shell <node>

# Use X-mode (mount /host, and do not enter host namespace)
	kubectl node-shell -x <node>

Execute custom command

	kubectl node-shell <node> -- echo 123

Use stdin

	cat /etc/passwd | kubectl node-shell <node> -- sh -c 'cat > /tmp/passwd'

Run oneliner script

	kubectl node-shell <node> -- sh -c 'cat /tmp/passwd; rm -f /tmp/passwd'

## Contacting lvv-service Team
[lvv-service team](https://devops.oci.oraclecorp.com/phonebook/network-automation) owns this service. Please reach out to them for any questions or concerns. 
