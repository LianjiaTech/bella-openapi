import React from 'react';
import Layout from '@theme/Layout';
import Translate, {translate} from '@docusaurus/Translate';

export default function NotFound(): React.ReactElement {
  return (
    <Layout
      title={translate({
        id: 'page.notFound.title',
        message: 'Page Not Found',
      })}>
      <main className="container margin-vert--xl">
        <div className="row">
          <div className="col col--6 col--offset-3">
            <h1 className="hero__title">
              <Translate
                id="page.notFound.title"
                description="The title of the 404 page">
                Page Not Found
              </Translate>
            </h1>
            <p>
              <Translate
                id="page.notFound.description"
                description="The description of the 404 page">
                We could not find what you were looking for.
              </Translate>
            </p>
            <p>
              <a href="/">
                <Translate
                  id="page.notFound.returnToHome"
                  description="The text for the return to home link on the 404 page">
                  Return to homepage
                </Translate>
              </a>
            </p>
          </div>
        </div>
      </main>
    </Layout>
  );
}