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
	"github.com/guijs/genealogy/internal/domain/projection"
)

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	membershipStore := family.NewInMemoryMembershipStore()
	personStore := person.NewInMemoryStore()
	kinshipStore := kinship.NewInMemoryStore()
	projectionStore := projection.NewInMemoryStore()
	objectStore := app.NewStubObjectStore()
	relationshipService := app.NewRelationshipService(personStore, kinshipStore)
	mediaService := app.NewMediaService(objectStore)
	graphService := app.NewGraphService(projectionStore)

	router := httpapi.NewRouter(httpapi.RouterDeps{
		MembershipStore:     membershipStore,
		RelationshipService: relationshipService,
		MediaService:        mediaService,
		GraphService:        graphService,
	})

	log.Printf("Starting server on :%s", port)
	if err := http.ListenAndServe(":"+port, router); err != nil {
		log.Fatalf("Server failed: %v", err)
	}
}
