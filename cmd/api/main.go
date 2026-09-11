package main

import (
	"log"
	"net/http"
	"os"

	httpapi "github.com/guijs/genealogy/internal/adapter/http"
	"github.com/guijs/genealogy/internal/app"
	"github.com/guijs/genealogy/internal/domain/family"
	"github.com/guijs/genealogy/internal/domain/kinship"
	"github.com/guijs/genealogy/internal/domain/person"
)

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	membershipStore := family.NewInMemoryMembershipStore()
	personStore := person.NewInMemoryStore()
	kinshipStore := kinship.NewInMemoryStore()
	relationshipService := app.NewRelationshipService(personStore, kinshipStore)

	router := httpapi.NewRouter(httpapi.RouterDeps{
		MembershipStore:     membershipStore,
		RelationshipService: relationshipService,
	})

	log.Printf("Starting server on :%s", port)
	if err := http.ListenAndServe(":"+port, router); err != nil {
		log.Fatalf("Server failed: %v", err)
	}
}
